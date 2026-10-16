package com.stock.dashboard.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.InputValidator;
import com.stock.dashboard.JwtUtil;
import com.stock.dashboard.config.HttpClientConfig;
import com.stock.dashboard.dao.CertificationUseDao;
import com.stock.dashboard.dao.RefreshTokenDao;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dao.UserSocialDao;
import com.stock.dashboard.dto.RefreshTokenDto;
import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.dto.UserIdentityResultDto;
import com.stock.dashboard.dto.UserLoginRequest;
import com.stock.dashboard.dto.UserSignupRequest;
import com.stock.dashboard.dto.UserSocialDto;
import com.stock.dashboard.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 기능 전체: 가입·로그인·소셜 로그인/연동·비밀번호·본인인증·탈퇴/복구·관심 종목·프로필 이미지.
 * (기능이 많아 고도화 목록 10번에서 Auth/OAuth/Account/Identity/Watchlist 로 나눌 예정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final CertificationUseDao   certificationUseDao;
    private final EmailService          emailService;
    private final InputValidator        validator;
    private final JwtUtil               jwtUtil;
    private final PortoneService        portoneService;
    private final RefreshTokenDao       refreshTokenDao;
    private final StockDao              stockDao;
    private final UserDao               userDao;
    private final UserSocialDao         userSocialDao;
    private final S3Service             s3Service;
    private final HttpClient            httpClient;
    private final ObjectMapper          objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int    MAX_LOGIN_FAILURES = 5;
    private static final String LOCKED_MESSAGE     = "로그인 시도 횟수 초과로 15분간 로그인이 제한됩니다. 잠시 후 다시 시도해주세요.";
    // 탈퇴 후 이 기간 안에는 계정을 복구할 수 있다.
    private static final long   RECOVERY_PERIOD_MS = 14L * 24 * 60 * 60 * 1000;
    private static final int    TEMP_PASSWORD_LENGTH = 10;
    // 헷갈리기 쉬운 글자(I, l, O, 0, 1)를 뺀 임시 비밀번호용 문자
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
    private static final long   MAX_PROFILE_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.base.url}")          private String appBaseUrl;
    @Value("${kakao.client-id}")       private String kakaoClientId;
    @Value("${kakao.client-secret}")   private String kakaoClientSecret;
    @Value("${kakao.redirect-uri}")    private String kakaoRedirectUri;
    @Value("${google.client-id}")      private String googleClientId;
    @Value("${google.client-secret}")  private String googleClientSecret;
    @Value("${google.redirect-uri}")   private String googleRedirectUri;

    /** 이메일 회원가입. 본인인증(PortOne) 결과와 입력한 이름·전화번호가 같아야 가입되고, 인증 메일을 보낸다. */
    @Transactional(rollbackFor = Exception.class)
    public void signup(UserSignupRequest req) throws Exception {
        validator.validateEmail(req.getEmail());
        validator.validatePassword(req.getPassword());
        validator.validateNickname(req.getNickname());
        validator.validatePhone(req.getPhone());

        if (checkEmailExists(req.getEmail()))
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        if (req.getImpUid() == null || req.getImpUid().isBlank())
            throw new IllegalArgumentException("본인인증이 필요합니다.");

        Map<String, String> certData = portoneService.getCertification(req.getImpUid());
        // 이름·전화번호는 사용자가 보낸 값이 아니라 본인인증 결과와 같아야 한다.
        if (!certData.get("name").equals(req.getName())
                || !normalizePhone(certData.get("phone")).equals(normalizePhone(req.getPhone())))
            throw new BusinessException("본인인증 정보와 입력한 이름 또는 휴대폰 번호가 다릅니다.");
        consumeCertification(req.getImpUid(), req.getEmail(), "SIGNUP");

        UserDto dto = new UserDto();
        dto.setEmail(req.getEmail());
        dto.setPassword(passwordEncoder.encode(req.getPassword()));
        dto.setName(certData.get("name"));
        dto.setNickname(req.getNickname());
        dto.setPhone(normalizePhone(certData.get("phone")));
        dto.setAddress(req.getAddress());
        dto.setAddressDetail(req.getAddressDetail());
        dto.setEmailVerifyToken(UUID.randomUUID().toString());

        userDao.insertUser(dto);
        emailService.sendVerificationEmail(dto.getEmail(), dto.getEmailVerifyToken());
    }

    /**
     * 이메일 로그인. 비밀번호를 5번 틀리면 15분 잠긴다(잠금 계산은 UserMapper.updateLoginFail).
     * 성공하면 액세스·리프레시 토큰과 사용자 정보를 돌려준다.
     */
    public Map<String, String> login(UserLoginRequest req) {
        // 관리자 계정 아이디는 이메일 형식이 아닌 "admin" 이라 형식 검사에서 뺀다.
        if (!"admin".equals(req.getEmail())) {
            validator.validateEmail(req.getEmail());
        }

        UserDto user = userDao.findByEmail(req.getEmail());
        if (user == null)
            throw new BusinessException("이메일 또는 비밀번호가 틀렸습니다.");
        ensureNotLocked(user);
        if (!"Y".equals(user.getEmailVerified()))
            throw new BusinessException("이메일 인증이 필요합니다.");

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            userDao.updateLoginFail(req.getEmail());
            // user 는 실패 횟수를 올리기 전에 읽은 값이므로 +1 해서 계산한다.
            int remaining = MAX_LOGIN_FAILURES - (user.getLoginFailCnt() + 1);
            if (remaining <= 0) throw new BusinessException(HttpStatus.FORBIDDEN, LOCKED_MESSAGE);
            throw new BusinessException("비밀번호가 틀렸습니다. 남은 시도: " + remaining + "회");
        }

        userDao.resetLoginFail(req.getEmail());
        Map<String, String> tokens = issueTokens(user);
        if ("Y".equals(user.getForcePwChange())) {
            tokens.put("forcePwChange", "Y");
        }
        return tokens;
    }

    public Map<String, String> kakaoLogin(String code) throws Exception {
        JsonNode info   = fetchKakaoUser(code, kakaoRedirectUri);
        String nickname = info.path("properties").path("nickname").asText("");
        if (nickname.isEmpty()) nickname = "카카오 사용자";
        return handleSocialLogin("KAKAO", "KAKAO_", kakaoEmail(info), nickname);
    }

    public Map<String, String> googleLogin(String code) throws Exception {
        JsonNode info   = fetchGoogleUser(code, googleRedirectUri);
        String nickname = info.path("name").asText("");
        if (nickname.isEmpty()) nickname = "구글 사용자";
        return handleSocialLogin("GOOGLE", "GOOGLE_", googleEmail(info), nickname);
    }

    /**
     * 소셜 로그인 공통 처리.
     * 1) 이 소셜 계정이 기존 회원에게 연동돼 있으면 그 회원으로 로그인한다.
     * 2) 아니면 소셜 이메일로 회원을 찾거나 새로 만든다(processSocialLogin).
     */
    private Map<String, String> handleSocialLogin(String provider, String prefix, String email, String nickname) {
        UserSocialDto linked = userSocialDao.selectByProviderAndEmail(provider, email);
        if (linked != null) {
            UserDto linkedUser = userDao.findById(linked.getUserId());
            if (linkedUser != null) {
                ensureNotLocked(linkedUser);
                Map<String, String> tokens = issueTokens(linkedUser);
                tokens.put("nickname", nickname);
                return tokens;
            }
        }
        return processSocialLogin(email, nickname, prefix);
    }

    /** 로그인한 사용자(email)에게 소셜 계정을 연결한다. email 은 서버가 발급한 state 에서 꺼낸 값이다. */
    public void linkSocial(String email, String provider, String code) throws Exception {
        if (code == null || code.isBlank()) throw new BusinessException("소셜 인증 정보가 없습니다. 다시 시도해주세요.");
        UserDto user = getUser(email);
        String linkRedirectUri = appBaseUrl + "/oauth/link";
        String providerEmail = switch (provider) {
            case "GOOGLE" -> googleEmail(fetchGoogleUser(code, linkRedirectUri));
            case "KAKAO"  -> kakaoEmail(fetchKakaoUser(code, linkRedirectUri));
            default -> throw new BusinessException("지원하지 않는 소셜 서비스입니다.");
        };

        // 이미 같은 서비스(카카오/구글)가 연동돼 있으면 아무것도 하지 않는다.
        if (userSocialDao.checkSocialLink(user.getUserId(), provider) > 0) {
            return;
        }
        UserSocialDto owner = userSocialDao.selectByProviderAndEmail(provider, providerEmail);
        if (owner != null && owner.getUserId() != user.getUserId()) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 다른 계정에 연동된 소셜 계정입니다.");
        }

        UserSocialDto dto = new UserSocialDto();
        dto.setUserId(user.getUserId());
        dto.setProvider(provider);
        dto.setProviderEmail(providerEmail);
        userSocialDao.insertSocial(dto);
    }

    // 소셜 로그인 2단계: 인가 code → 소셜 서비스의 액세스 토큰 → 사용자 정보(JSON)
    private JsonNode fetchKakaoUser(String code, String redirectUri) throws Exception {
        String tokenBody = formBody(Map.of(
            "grant_type", "authorization_code", "client_id", kakaoClientId,
            "client_secret", kakaoClientSecret, "redirect_uri", redirectUri, "code", code));
        String kakaoToken = fetchAccessToken("https://kauth.kakao.com/oauth/token", tokenBody);
        return fetchUserInfo("https://kapi.kakao.com/v2/user/me", kakaoToken);
    }

    private JsonNode fetchGoogleUser(String code, String redirectUri) throws Exception {
        String tokenBody = formBody(Map.of(
            "grant_type", "authorization_code", "client_id", googleClientId,
            "client_secret", googleClientSecret, "redirect_uri", redirectUri, "code", code));
        String googleToken = fetchAccessToken("https://oauth2.googleapis.com/token", tokenBody);
        return fetchUserInfo("https://www.googleapis.com/oauth2/v2/userinfo", googleToken);
    }

    /**
     * 카카오가 "인증된 이메일"이라고 확인한 경우에만 그 이메일을 쓴다.
     * 인증되지 않은 이메일을 믿으면, 남의 이메일을 적어 둔 카카오 계정으로 그 사람 계정에 로그인할 수 있다.
     */
    private String kakaoEmail(JsonNode info) {
        String id = info.path("id").asText("");
        if (id.isEmpty()) throw new BusinessException(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 확인할 수 없습니다.");
        JsonNode account = info.path("kakao_account");
        String email = account.path("email").asText("");
        boolean verified = account.path("is_email_verified").asBoolean(false)
                && account.path("is_email_valid").asBoolean(true);
        return verified && !email.isEmpty() ? email : "kakao_" + id + "@kakao.com";
    }

    private String googleEmail(JsonNode info) {
        String id = info.path("id").asText("");
        if (id.isEmpty()) throw new BusinessException(HttpStatus.BAD_GATEWAY, "구글 사용자 정보를 확인할 수 없습니다.");
        String email = info.path("email").asText("");
        boolean verified = info.path("verified_email").asBoolean(false);
        return verified && !email.isEmpty() ? email : "google_" + id + "@google.com";
    }

    /**
     * 잠긴 계정은 LOCKED_UNTIL(마지막 실패 후 15분)이 지나면 자동으로 풀린다.
     * 예전에는 관리자만 풀 수 있어서, 남의 이메일로 5번 틀리면 그 계정을 영구히 막을 수 있었다.
     * LOCKED_UNTIL 이 없는 잠금(관리자 처리 대상)은 그대로 막는다.
     */
    private void ensureNotLocked(UserDto user) {
        if (!"Y".equals(user.getAccountLocked())) return;
        Date until = user.getLockedUntil();
        if (until == null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "계정이 잠겼습니다. 관리자에게 문의하세요.");
        if (new Date().before(until))
            throw new BusinessException(HttpStatus.FORBIDDEN, LOCKED_MESSAGE);
        userDao.resetLoginFail(user.getEmail());
        user.setAccountLocked("N");
        user.setLoginFailCnt(0);
        user.setLockedUntil(null);
    }

    /** 같은 본인인증 결과(imp_uid)를 두 번 쓰지 못하게 기록한다. */
    private void consumeCertification(String impUid, String email, String purpose) {
        try {
            certificationUseDao.insertUse(impUid, email, purpose);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용된 본인인증입니다. 다시 인증해주세요.");
        }
    }

    /** 리프레시 토큰으로 새 액세스 토큰을 발급한다. 서명이 맞아도 DB 에 저장된 토큰(마지막 로그인)이어야 한다. */
    public Map<String, String> refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken))
            throw new BusinessException("유효하지 않은 Refresh Token입니다.");
        RefreshTokenDto rtDto = refreshTokenDao.findByToken(refreshToken);
        if (rtDto == null)
            throw new BusinessException("만료되거나 존재하지 않는 Refresh Token입니다.");
        String email = jwtUtil.getEmailFromRefresh(refreshToken);
        UserDto user = userDao.findByEmail(email);
        return Map.of("accessToken",
            jwtUtil.generateAccessToken(email, user != null ? user.getRole() : "USER"));
    }

    public void logout(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) return;
        UserDto user = userDao.findByEmail(jwtUtil.getEmailFromRefresh(refreshToken));
        if (user != null) refreshTokenDao.deleteByUserId(user.getUserId());
    }

    public void addWatchlist(String email, int itemId) {
        userDao.insertWatchlist(getUser(email).getUserId(), itemId);
    }

    /** 비밀번호 재설정 메일을 보낸다. 링크의 토큰은 1시간 동안 유효하다. */
    public void forgotPassword(String email) throws Exception {
        UserDto user = userDao.findByEmail(email);
        if (user == null) throw new BusinessException("존재하지 않는 이메일입니다.");

        String token = UUID.randomUUID().toString();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 1);

        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(cal.getTime());
        userDao.updatePasswordResetToken(user);
        emailService.sendPasswordResetEmail(email, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        validator.validatePassword(newPassword);

        UserDto user = userDao.findByPasswordResetToken(token);
        if (user == null) throw new BusinessException("유효하지 않은 링크입니다.");
        if (new Date().after(user.getPasswordResetExpiry()))
            throw new BusinessException("만료된 링크입니다. 다시 요청해 주세요.");

        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updatePassword(user);
        userDao.clearPasswordResetToken(user.getUserId());
    }

    /**
     * 가입 화면의 본인인증 확인. 인증 결과(이름·전화번호)를 돌려주고,
     * 같은 사람이 이미 가입했으면 기존 계정 정보(가입 방식, 가린 이메일)도 함께 알려준다.
     */
    public Map<String, Object> verifyCertification(String impUid) throws Exception {
        Map<String, String> cert = portoneService.getCertification(impUid);
        Map<String, Object> res = new HashMap<>(cert);

        String name  = cert.get("name");
        String phoneNormalized = normalizePhone(cert.get("phone"));

        UserIdentityResultDto found =
            (name != null && !name.isBlank() && !phoneNormalized.isBlank())
                ? userDao.findActiveMemberByIdentity(name, phoneNormalized)
                : null;

        if (found != null) {
            res.put("existingMember", true);
            res.put("existingProvider", found.getProvider() != null ? found.getProvider() : "EMAIL");
            res.put("existingMaskedEmail", maskEmail(found.getEmail()));
        } else {
            res.put("existingMember", false);
            res.put("existingProvider", null);
            res.put("existingMaskedEmail", null);
        }
        return res;
    }

    public boolean verifyEmail(String token) {
        UserDto user = userDao.findByEmailVerifyToken(token);
        if (user == null) return false;
        userDao.updateEmailVerified(token);
        return true;
    }

    public boolean checkEmailExists(String email) {
        return userDao.checkEmailExists(email) > 0;
    }

    public boolean checkNicknameExists(String nickname) {
        return userDao.checkNicknameExists(nickname) > 0;
    }

    public List<UserSocialDto> getSocialLinks(String email) {
        return userSocialDao.selectByUserId(getUser(email).getUserId());
    }

    public Map<String, String> getUserInfo(String email) {
        UserDto user = getUser(email);
        Map<String, String> info = new HashMap<>();

        info.put("email", user.getEmail());
        info.put("nickname", user.getNickname());

        String dateStr = "";
        if (user.getCreatedAt() != null) {
            dateStr = new SimpleDateFormat("yyyy-MM-dd").format(user.getCreatedAt());
        }
        info.put("createdAt", dateStr);

        String profileImageUrl = userDao.findProfileImageUrl(user.getUserId());
        info.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");

        return info;
    }

    public List<Integer> getWatchlist(String email) {
        return userDao.selectWatchlist(getUser(email).getUserId());
    }

    public List<StockPriceDto> getWatchlistDetail(String email) {
        List<Integer> itemIds = userDao.selectWatchlist(getUser(email).getUserId());
        if (itemIds.isEmpty()) return new ArrayList<>();
        return stockDao.selectLatestPricesByItemIds(itemIds);
    }

    /** 로그인한 사용자의 비밀번호 변경. 5분 안에 받은 본인인증 토큰(verifyToken)이 있어야 한다. */
    public void changePassword(String email, String verifyToken, String newPassword) {
        requireVerifyToken(verifyToken);
        validator.validatePassword(newPassword);
        UserDto user = getVerifiedUser(email, verifyToken);
        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updatePassword(user);
        if ("Y".equals(user.getForcePwChange())) {
            userDao.updateForcePwChange(user.getUserId(), "N");
        }
    }

    public void resendVerifyEmail(String email) throws Exception {
        UserDto user = userDao.findByEmail(email);
        if (user == null) throw new BusinessException("존재하지 않는 이메일입니다.");
        if ("Y".equals(user.getEmailVerified())) throw new BusinessException("이미 인증된 이메일입니다.");
        String token = UUID.randomUUID().toString();
        user.setEmailVerifyToken(token);
        userDao.updateEmailVerifyToken(user);
        emailService.sendVerificationEmail(email, token);
    }

    public void updateNickname(String email, String nickname) {
        validator.validateNickname(nickname);
        UserDto user = getUser(email);
        if (!nickname.equals(user.getNickname()) && checkNicknameExists(nickname))
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        user.setNickname(nickname);
        userDao.updateNickname(user);
    }

    /**
     * 회원 탈퇴. 바로 지우지 않고 DELETED_AT 만 기록한다(소프트 삭제).
     * 14일 안에는 recoverAccount 로 되살릴 수 있다.
     */
    @Transactional
    public void deleteAccount(String email, String verifyToken, String deleteReason) {
        requireVerifyToken(verifyToken);
        UserDto user = getVerifiedUser(email, verifyToken);
        refreshTokenDao.deleteByUserId(user.getUserId());
        userDao.softDeleteUser(user.getUserId(), deleteReason != null ? deleteReason : "");
    }

    public void removeWatchlist(String email, int itemId) {
        userDao.deleteWatchlist(getUser(email).getUserId(), itemId);
    }

    public void unlinkSocial(String email, String provider) {
        userSocialDao.deleteSocial(getUser(email).getUserId(), provider.toUpperCase());
    }

    /** 비밀번호 변경·탈퇴 전에 휴대폰 본인인증을 확인하고, 5분짜리 본인인증 토큰(verifyToken)을 발급한다. */
    @Transactional
    public Map<String, String> verifyIdentityForAction(String email, String impUid) {
        UserDto user = getUser(email);
        if (impUid == null || impUid.isBlank()) throw new BusinessException("본인인증 정보가 없습니다.");

        Map<String, String> certData;
        try {
            certData = portoneService.getCertification(impUid);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[본인인증] PortOne 인증 조회 실패: {}", e.getMessage(), e);
            throw new BusinessException("본인인증 정보 확인에 실패했습니다.");
        }

        // PortoneService 가 "완료된 인증 + 이름·전화번호 있음"을 보장한다.
        String certName  = certData.get("name");
        String certPhone = normalizePhone(certData.get("phone"));

        // 이름·전화번호를 등록한 회원은 인증 결과와 반드시 같아야 한다.
        boolean hasIdentity = user.getName() != null && !user.getName().isBlank()
                && user.getPhone() != null && !user.getPhone().isBlank();
        if (hasIdentity && (!user.getName().equals(certName) || !normalizePhone(user.getPhone()).equals(certPhone))) {
            throw new BusinessException("본인인증 정보가 회원 정보와 일치하지 않습니다.");
        }
        // 소셜 가입자처럼 등록 정보가 없으면 첫 본인인증 결과를 등록한다. 다음부터는 이 정보와 대조한다.
        if (!hasIdentity) {
            UserIdentityResultDto owner = userDao.findActiveMemberByIdentity(certName, certPhone);
            if (owner != null && owner.getUserId() != user.getUserId())
                throw new BusinessException(HttpStatus.CONFLICT, "이미 다른 계정에 등록된 본인인증 정보입니다.");
            userDao.updateIdentity(user.getUserId(), certName, certPhone);
        }
        consumeCertification(impUid, user.getEmail(), "VERIFY");

        String verifyToken = jwtUtil.generateVerifyToken(user.getEmail());
        return Map.of("verifyToken", verifyToken);
    }

    /**
     * 탈퇴 14일 이내 계정 복구. 본인 이름·전화번호가 맞으면 계정을 되살리고
     * 임시 비밀번호를 메일로 보낸 뒤, 다음 로그인 때 비밀번호를 바꾸도록 표시한다.
     */
    @Transactional
    public Map<String, String> recoverAccount(String email, String name, String phone) {
        UserDto deletedUser = userDao.findByEmailIncludeDeleted(email);
        if (deletedUser == null || deletedUser.getDeletedAt() == null) {
            throw new BusinessException("복구 가능한 계정이 없습니다.");
        }
        if (!isWithinRecoveryPeriod(deletedUser)) {
            throw new BusinessException("복구 기간(2주)이 만료된 계정입니다.");
        }
        if (name == null || phone == null
                || !name.equals(deletedUser.getName())
                || !normalizePhone(phone).equals(normalizePhone(deletedUser.getPhone()))) {
            throw new BusinessException("본인인증 정보가 탈퇴 계정 정보와 일치하지 않습니다.");
        }
        String tempPassword = generateTempPassword();
        deletedUser.setPassword(passwordEncoder.encode(tempPassword));
        userDao.updatePassword(deletedUser);
        userDao.restoreUser(deletedUser.getUserId());
        userDao.updateForcePwChange(deletedUser.getUserId(), "Y");
        try {
            emailService.sendTempPasswordEmail(email, tempPassword);
        } catch (Exception e) {
            log.error("[복구] 임시 비밀번호 이메일 발송 실패: {}", e.getMessage(), e);
            throw new BusinessException("임시 비밀번호 이메일 발송에 실패했습니다.");
        }
        return Map.of("message", "계정이 복구되었습니다. 이메일로 발송된 임시 비밀번호로 로그인해주세요.");
    }

    /** 가입 화면에서 쓰는 확인: 이 이메일이 복구 가능한(탈퇴 14일 이내) 계정인지 알려준다. */
    public Map<String, Object> checkDeletedAccount(String email) {
        UserDto user = userDao.findByEmailIncludeDeleted(email);
        boolean recoverable = user != null && user.getDeletedAt() != null && isWithinRecoveryPeriod(user);
        return Map.of("deleted", recoverable, "recoverable", recoverable);
    }

    private static boolean isWithinRecoveryPeriod(UserDto deletedUser) {
        long elapsedMs = System.currentTimeMillis() - deletedUser.getDeletedAt().getTime();
        return elapsedMs <= RECOVERY_PERIOD_MS;
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private void requireVerifyToken(String verifyToken) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new BusinessException("본인인증이 필요합니다.");
        }
        if (!jwtUtil.validateVerifyToken(verifyToken)) {
            throw new BusinessException("본인인증이 만료되었습니다. 다시 인증해주세요.");
        }
    }

    // 본인인증 토큰은 로그인한 본인의 것이어야 한다(남이 받은 토큰으로 내 계정을 바꾸지 못하게).
    private UserDto getVerifiedUser(String email, String verifyToken) {
        UserDto user = getUser(email);
        if (!user.getEmail().equals(jwtUtil.getEmailFromVerifyToken(verifyToken))) {
            throw new BusinessException("본인인증 정보가 일치하지 않습니다.");
        }
        return user;
    }

    private String fetchAccessToken(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode node = objectMapper.readTree(res.body());
        if (!node.has("access_token")) {
            log.warn("[소셜로그인] 토큰 교환 실패: url={}, status={}", url, res.statusCode());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "소셜 로그인 서버 응답을 확인할 수 없습니다.");
        }
        return node.get("access_token").asText();
    }

    private JsonNode fetchUserInfo(String url, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("[소셜로그인] 사용자 정보 조회 실패: url={}, status={}", url, res.statusCode());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "소셜 로그인 서버 응답을 확인할 수 없습니다.");
        }
        return objectMapper.readTree(res.body());
    }

    private static String formBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(v != null ? v : "", StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static String normalizePhone(String phone) {
        return phone != null ? phone.replace("-", "") : "";
    }

    public long getUserId(String email) {
        return getUser(email).getUserId();
    }

    private UserDto getUser(String email) {
        UserDto user = email != null ? userDao.findByEmail(email) : null;
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다. 다시 로그인해주세요.");
        return user;
    }

    // 로그인 성공 시 토큰 발급. 리프레시 토큰은 사용자당 하나만 DB 에 두어, 새로 로그인하면 예전 기기의 토큰은 무효가 된다.
    private Map<String, String> issueTokens(UserDto user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        refreshTokenDao.deleteByUserId(user.getUserId());

        RefreshTokenDto rtDto = new RefreshTokenDto();
        rtDto.setUserId(user.getUserId());
        rtDto.setToken(refreshToken);
        rtDto.setExpiredAt(jwtUtil.getRefreshExpiredAt());
        refreshTokenDao.insertRefreshToken(rtDto);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken",  accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("email",        user.getEmail());
        tokens.put("userId",       String.valueOf(user.getUserId()));
        tokens.put("role",         user.getRole() != null ? user.getRole() : "USER");
        tokens.put("nickname",     user.getNickname() != null ? user.getNickname() : "");
        return tokens;
    }

    // 소셜 이메일로 회원을 찾고, 없으면 새로 만든다. 탈퇴한 계정은 복구 전까지 로그인할 수 없다.
    private Map<String, String> processSocialLogin(String email, String nickname, String prefix) {
        UserDto user = userDao.findByEmailIncludeDeleted(email);
        if (user != null && user.getDeletedAt() != null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "탈퇴 처리된 계정입니다. 계정 복구 후 이용해주세요.");
        if (user != null) {
            ensureNotLocked(user);
        } else {
            // 소셜 회원은 비밀번호로 로그인하지 않는다. 그래도 컬럼이 비지 않도록 아무도 모르는 값을 넣는다.
            UserDto newUser = new UserDto();
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(prefix + System.currentTimeMillis()));
            userDao.insertUser(newUser);
            user = userDao.findByEmail(email);
        }

        Map<String, String> tokens = issueTokens(user);
        tokens.put("nickname", nickname);
        return tokens;
    }

    /** 프로필 이미지를 S3 에 올리고, 예전 S3 이미지는 지운다. 새 이미지 주소를 돌려준다. */
    public String updateProfileImage(Long userId, MultipartFile file) throws IOException {
        validateImageFile(file);
        String currentImageUrl = userDao.findProfileImageUrl(userId);
        if (currentImageUrl != null && currentImageUrl.contains("amazonaws.com")) {
            s3Service.delete(currentImageUrl);
        }
        String newImageUrl = s3Service.upload(file, "profiles");
        userDao.updateProfileImageUrl(userId, newImageUrl);
        return newImageUrl;
    }

    // 기존 계정 안내용으로 이메일 앞 두 글자만 보여준다. 예) hong@test.com → ho***@test.com
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        if (isAutoGeneratedEmail(email)) return null;
        int at = email.indexOf('@');
        if (at < 1) return null;
        String local  = email.substring(0, at);
        String domain = email.substring(at);
        return (local.length() == 1 ? local : local.substring(0, 2)) + "***" + domain;
    }

    private boolean isAutoGeneratedEmail(String email) {
        return email != null && (email.startsWith("kakao_") || email.startsWith("google_"));
    }

    // 형식은 브라우저가 보낸 Content-Type 만 본다(파일 내용 검사는 고도화 목록 13번).
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("이미지 파일이 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("이미지 파일만 업로드 가능합니다.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new BusinessException("파일 크기는 5MB 이하만 가능합니다.");
        }
    }
}
