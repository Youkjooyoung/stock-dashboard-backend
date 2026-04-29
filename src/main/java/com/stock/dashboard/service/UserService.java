package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.InputValidator;
import com.stock.dashboard.JwtUtil;
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
import com.stock.dashboard.util.AesEncryptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AesEncryptor          aesEncryptor;
    private final EmailService          emailService;
    private final InputValidator        validator;
    private final JwtUtil               jwtUtil;
    private final PortoneService        portoneService;
    private final RefreshTokenDao       refreshTokenDao;
    private final StockDao              stockDao;
    private final UserDao               userDao;
    private final UserSocialDao         userSocialDao;
    private final S3Service             s3Service;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.base.url}")          private String appBaseUrl;
    @Value("${kakao.client-id}")       private String kakaoClientId;
    @Value("${kakao.client-secret}")   private String kakaoClientSecret;
    @Value("${kakao.redirect-uri}")    private String kakaoRedirectUri;
    @Value("${google.client-id}")      private String googleClientId;
    @Value("${google.client-secret}")  private String googleClientSecret;
    @Value("${google.redirect-uri}")   private String googleRedirectUri;

    public void signup(UserSignupRequest req) throws Exception {
        validator.validateEmail(req.getEmail());
        validator.validatePassword(req.getPassword());
        validator.validateNickname(req.getNickname());
        validator.validateResidentNo(req.getResidentNo());
        validator.validatePhone(req.getPhone());

        if (checkEmailExists(req.getEmail()))
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        if (req.getImpUid() == null || req.getImpUid().isBlank())
            throw new IllegalArgumentException("본인인증이 필요합니다.");

        Map<String, String> certData = portoneService.getCertification(req.getImpUid());
        validateResidentNoByCert(req.getResidentNo(), certData.get("birth"));

        UserDto dto = new UserDto();
        dto.setEmail(req.getEmail());
        dto.setPassword(passwordEncoder.encode(req.getPassword()));
        dto.setName(req.getName());
        dto.setNickname(req.getNickname());
        dto.setPhone(req.getPhone());
        dto.setAddress(req.getAddress());
        dto.setAddressDetail(req.getAddressDetail());
        dto.setResidentNo(aesEncryptor.encrypt(req.getResidentNo()));
        dto.setEmailVerifyToken(UUID.randomUUID().toString());

        userDao.insertUser(dto);
        emailService.sendVerificationEmail(dto.getEmail(), dto.getEmailVerifyToken());
    }

    public String register(UserDto dto) {
        validator.validateEmail(dto.getEmail());
        validator.validatePassword(dto.getPassword());
        if (userDao.findByEmail(dto.getEmail()) != null)
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        dto.setPassword(passwordEncoder.encode(dto.getPassword()));
        userDao.insertUser(dto);
        return "회원가입 완료";
    }

    public Map<String, String> login(UserLoginRequest req) {
        if (!"admin".equals(req.getEmail())) {
            validator.validateEmail(req.getEmail());
        }

        UserDto user = userDao.findByEmail(req.getEmail());
        if (user == null)
            throw new RuntimeException("이메일 또는 비밀번호가 틀렸습니다.");
        if ("Y".equals(user.getAccountLocked()))
            throw new RuntimeException("로그인 시도 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요.");
        if (!"Y".equals(user.getEmailVerified()))
            throw new RuntimeException("이메일 인증이 필요합니다.");

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            userDao.updateLoginFail(req.getEmail());
            int remaining = 5 - (user.getLoginFailCnt() + 1);
            if (remaining <= 0) throw new RuntimeException("로그인 시도 횟수 초과로 계정이 잠겼습니다.");
            throw new RuntimeException("비밀번호가 틀렸습니다. 남은 시도: " + remaining + "회");
        }

        userDao.resetLoginFail(req.getEmail());
        Map<String, String> tokens = issueTokens(user);
        if ("Y".equals(user.getForcePwChange())) {
            tokens.put("forcePwChange", "Y");
        }
        return tokens;
    }

    public Map<String, String> kakaoLogin(String code) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String tokenBody = String.format(
            "grant_type=authorization_code&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
            kakaoClientId, kakaoClientSecret, kakaoRedirectUri, code
        );
        String kakaoToken = fetchAccessToken(client, "https://kauth.kakao.com/oauth/token", tokenBody);
        JsonNode info = fetchUserInfo(client, "https://kapi.kakao.com/v2/user/me", kakaoToken);
        String email    = info.path("kakao_account").path("email").asText();
        String nickname = info.path("properties").path("nickname").asText();
        if (email.isEmpty())    email    = "kakao_" + info.path("id").asText() + "@kakao.com";
        if (nickname.isEmpty()) nickname = "카카오 사용자";
        return handleSocialLogin("KAKAO", "KAKAO_", email, nickname);
    }

    public Map<String, String> googleLogin(String code) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String tokenBody = String.format(
            "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
            code, googleClientId, googleClientSecret, googleRedirectUri
        );
        String googleToken = fetchAccessToken(client, "https://oauth2.googleapis.com/token", tokenBody);
        JsonNode info = fetchUserInfo(client, "https://www.googleapis.com/oauth2/v2/userinfo", googleToken);
        String email    = info.path("email").asText();
        String nickname = info.path("name").asText();
        if (email.isEmpty()) {
            String id = info.path("id").asText();
            email = "google_" + (id.isEmpty() ? System.currentTimeMillis() : id) + "@google.com";
        }
        if (nickname.isEmpty()) nickname = "구글 사용자";
        return handleSocialLogin("GOOGLE", "GOOGLE_", email, nickname);
    }

    private Map<String, String> handleSocialLogin(String provider, String prefix, String email, String nickname) {
        UserSocialDto linked = userSocialDao.selectByProviderAndEmail(provider, email);
        if (linked != null) {
            UserDto linkedUser = userDao.findById(linked.getUserId());
            if (linkedUser != null) {
                Map<String, String> tokens = issueTokens(linkedUser);
                tokens.put("nickname", nickname);
                return tokens;
            }
        }
        return processSocialLogin(email, nickname, prefix);
    }
    public void linkGoogle(String token, String code) throws Exception {
        UserDto user = getUser(jwtUtil.getEmailFromAccess(token));
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
            code, googleClientId, googleClientSecret, appBaseUrl + "/oauth/link"
        );

        String googleToken = fetchAccessToken(client, "https://oauth2.googleapis.com/token", tokenBody);
        JsonNode userJson  = fetchUserInfo(client, "https://www.googleapis.com/oauth2/v2/userinfo", googleToken);

        String email = userJson.path("email").asText();
        if (email.isEmpty()) email = "google_" + userJson.path("id").asText() + "@google.com";

        if (userSocialDao.checkSocialLink(user.getUserId(), "GOOGLE") > 0) {
            return;
        }

        UserSocialDto dto = new UserSocialDto();
        dto.setUserId(user.getUserId());
        dto.setProvider("GOOGLE");
        dto.setProviderEmail(email);
        userSocialDao.insertSocial(dto);
    }

    public void linkKakao(String token, String code) throws Exception {
        UserDto user = getUser(jwtUtil.getEmailFromAccess(token));
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "grant_type=authorization_code&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
            kakaoClientId, kakaoClientSecret, appBaseUrl + "/oauth/link", code
        );

        String kakaoToken = fetchAccessToken(client, "https://kauth.kakao.com/oauth/token", tokenBody);
        JsonNode userJson = fetchUserInfo(client, "https://kapi.kakao.com/v2/user/me", kakaoToken);

        String email = userJson.path("kakao_account").path("email").asText();
        if (email.isEmpty()) email = "kakao_" + userJson.path("id").asText() + "@kakao.com";

        if (userSocialDao.checkSocialLink(user.getUserId(), "KAKAO") > 0) {
            return;
        }

        UserSocialDto dto = new UserSocialDto();
        dto.setUserId(user.getUserId());
        dto.setProvider("KAKAO");
        dto.setProviderEmail(email);
        userSocialDao.insertSocial(dto);
    }

    public Map<String, String> refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken))
            throw new RuntimeException("유효하지 않은 Refresh Token입니다.");
        RefreshTokenDto rtDto = refreshTokenDao.findByToken(refreshToken);
        if (rtDto == null)
            throw new RuntimeException("만료되거나 존재하지 않는 Refresh Token입니다.");
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

    public void forgotPassword(String email) throws Exception {
        UserDto user = userDao.findByEmail(email);
        if (user == null) throw new RuntimeException("존재하지 않는 이메일입니다.");

        String token = UUID.randomUUID().toString();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 1);

        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(cal.getTime());
        userDao.updatePasswordResetToken(user);
        emailService.sendPasswordResetEmail(email, token);
    }

    public void resetPassword(String token, String newPassword) {
        validator.validatePassword(newPassword);

        UserDto user = userDao.findByPasswordResetToken(token);
        if (user == null) throw new RuntimeException("유효하지 않은 링크입니다.");
        if (new Date().after(user.getPasswordResetExpiry()))
            throw new RuntimeException("만료된 링크입니다. 다시 요청해 주세요.");

        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updatePassword(user);
        userDao.clearPasswordResetToken(user.getUserId());
    }

    public Map<String, Object> verifyCertification(String impUid) throws Exception {
        Map<String, String> cert = portoneService.getCertification(impUid);
        Map<String, Object> res = new HashMap<>(cert);

        String name  = cert.get("name");
        String phone = cert.get("phone");
        String phoneNormalized = phone != null ? phone.replaceAll("-", "") : "";

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

    public UserDto findByEmail(String email) {
        return userDao.findByEmail(email);
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

    public void saveSocialLink(String email, UserSocialDto dto) {
        dto.setUserId(getUser(email).getUserId());
        dto.setProvider(dto.getProvider().toUpperCase());
        userSocialDao.insertSocial(dto);
    }

    public void changePassword(String email, String verifyToken, String newPassword) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new RuntimeException("본인인증이 필요합니다.");
        }
        if (!jwtUtil.validateVerifyToken(verifyToken)) {
            throw new RuntimeException("본인인증이 만료되었습니다. 다시 인증해주세요.");
        }
        validator.validatePassword(newPassword);
        UserDto user = getUser(email);
        String verifyEmail = jwtUtil.getEmailFromVerifyToken(verifyToken);
        if (!user.getEmail().equals(verifyEmail)) {
            throw new RuntimeException("본인인증 정보가 일치하지 않습니다.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updatePassword(user);
        if ("Y".equals(user.getForcePwChange())) {
            userDao.updateForcePwChange(user.getUserId(), "N");
        }
    }

    public void resendVerifyEmail(String email) throws Exception {
        UserDto user = userDao.findByEmail(email);
        if (user == null) throw new RuntimeException("존재하지 않는 이메일입니다.");
        if ("Y".equals(user.getEmailVerified())) throw new RuntimeException("이미 인증된 이메일입니다.");
        String token = UUID.randomUUID().toString();
        user.setEmailVerifyToken(token);
        userDao.updateEmailVerifyToken(user);
        emailService.sendVerificationEmail(email, token);
    }

    public void updateNickname(String email, String nickname) {
        validator.validateNickname(nickname);
        UserDto user = getUser(email);
        user.setNickname(nickname);
        userDao.updateNickname(user);
    }

    public void deleteAccount(String email, String verifyToken, String deleteReason) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new RuntimeException("본인인증이 필요합니다.");
        }
        if (!jwtUtil.validateVerifyToken(verifyToken)) {
            throw new RuntimeException("본인인증이 만료되었습니다. 다시 인증해주세요.");
        }
        UserDto user = getUser(email);
        String verifyEmail = jwtUtil.getEmailFromVerifyToken(verifyToken);
        if (!user.getEmail().equals(verifyEmail)) {
            throw new RuntimeException("본인인증 정보가 일치하지 않습니다.");
        }
        refreshTokenDao.deleteByUserId(user.getUserId());
        userDao.softDeleteUser(user.getUserId(), deleteReason != null ? deleteReason : "");
    }

    public void removeWatchlist(String email, int itemId) {
        userDao.deleteWatchlist(getUser(email).getUserId(), itemId);
    }

    public void unlinkSocial(String email, String provider) {
        userSocialDao.deleteSocial(getUser(email).getUserId(), provider.toUpperCase());
    }

    public Map<String, String> verifyIdentityForAction(String token, String impUid) {
        UserDto user = getUser(jwtUtil.getEmailFromAccess(token));
        if (user == null) throw new RuntimeException("사용자 정보를 찾을 수 없습니다.");
        if (impUid == null || impUid.isBlank()) throw new RuntimeException("본인인증 정보가 없습니다.");

        Map<String, String> certData;
        try {
            certData = portoneService.getCertification(impUid);
        } catch (Exception e) {
            log.error("[본인인증] PortOne 인증 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("본인인증 정보 확인에 실패했습니다.");
        }

        String certName  = certData.get("name");
        String certPhone = certData.get("phone");

        if (certName == null || certPhone == null) {
            throw new RuntimeException("본인인증 정보를 가져올 수 없습니다.");
        }

        String userName  = user.getName() != null ? user.getName() : "";
        String userPhone = user.getPhone() != null ? user.getPhone() : "";
        String normalizedCertPhone = certPhone.replaceAll("-", "");
        String normalizedUserPhone = userPhone.replaceAll("-", "");

        if (!userName.equals(certName) || !normalizedUserPhone.equals(normalizedCertPhone)) {
            throw new RuntimeException("본인인증 정보가 회원 정보와 일치하지 않습니다.");
        }

        String verifyToken = jwtUtil.generateVerifyToken(user.getEmail());
        return Map.of("verifyToken", verifyToken);
    }

    public Map<String, String> recoverAccount(String email, String name, String phone) {
        UserDto deletedUser = userDao.findByEmailIncludeDeleted(email);
        if (deletedUser == null || deletedUser.getDeletedAt() == null) {
            throw new RuntimeException("복구 가능한 계정이 없습니다.");
        }
        long diffMs = System.currentTimeMillis() - deletedUser.getDeletedAt().getTime();
        long twoWeeksMs = 14L * 24 * 60 * 60 * 1000;
        if (diffMs > twoWeeksMs) {
            throw new RuntimeException("복구 기간(2주)이 만료된 계정입니다.");
        }
        if (!deletedUser.getName().equals(name) || !deletedUser.getPhone().equals(phone)) {
            throw new RuntimeException("본인인증 정보가 탈퇴 계정 정보와 일치하지 않습니다.");
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
            throw new RuntimeException("임시 비밀번호 이메일 발송에 실패했습니다.");
        }
        return Map.of("message", "계정이 복구되었습니다. 이메일로 발송된 임시 비밀번호로 로그인해주세요.");
    }

    public Map<String, Object> checkDeletedAccount(String email) {
        UserDto user = userDao.findByEmailIncludeDeleted(email);
        Map<String, Object> result = new HashMap<>();
        if (user != null && user.getDeletedAt() != null) {
            long diffMs = System.currentTimeMillis() - user.getDeletedAt().getTime();
            long twoWeeksMs = 14L * 24 * 60 * 60 * 1000;
            if (diffMs <= twoWeeksMs) {
                result.put("deleted", true);
                result.put("recoverable", true);
                return result;
            }
        }
        result.put("deleted", false);
        result.put("recoverable", false);
        return result;
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String fetchAccessToken(HttpClient client, String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        JsonNode node = new ObjectMapper().readTree(res.body());
        if (!node.has("access_token")) {
            throw new RuntimeException("API 응답 에러: " + res.body());
        }
        return node.get("access_token").asText();
    }

    private JsonNode fetchUserInfo(HttpClient client, String url, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(res.body());
    }

    public long getUserId(String email) {
        return getUser(email).getUserId();
    }

    private UserDto getUser(String email) {
        return userDao.findByEmail(email);
    }

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

    private Map<String, String> processSocialLogin(String email, String nickname, String prefix) {
        UserDto user = userDao.findByEmail(email);
        if (user == null) {
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
    public String updateProfileImage(Long userId, org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        validateImageFile(file);
        String currentImageUrl = userDao.findProfileImageUrl(userId);
        if (currentImageUrl != null && currentImageUrl.contains("amazonaws.com")) {
            s3Service.delete(currentImageUrl);
        }
        String newImageUrl = s3Service.upload(file, "profiles");
        userDao.updateProfileImageUrl(userId, newImageUrl);
        return newImageUrl;
    }

    private void validateResidentNoByCert(String residentNo, String birth) {
        if (birth == null || birth.isBlank())
            throw new IllegalArgumentException("본인인증 생년월일 정보를 확인할 수 없습니다.");

        String birthYYMMDD = birth.replace("-", "").substring(2);
        String rrnFront    = residentNo.substring(0, 6);

        if (!birthYYMMDD.equals(rrnFront))
            throw new IllegalArgumentException("주민등록번호가 본인인증 정보와 일치하지 않습니다.");

        int genderCode = Integer.parseInt(residentNo.substring(6, 7));
        int birthYear  = Integer.parseInt(birth.substring(0, 4));

        boolean is1900s = birthYear >= 1900 && birthYear <= 1999;
        boolean is2000s = birthYear >= 2000 && birthYear <= 2099;

        if (is1900s && genderCode != 1 && genderCode != 2)
            throw new IllegalArgumentException("주민등록번호가 본인인증 정보와 일치하지 않습니다.");
        if (is2000s && genderCode != 3 && genderCode != 4)
            throw new IllegalArgumentException("주민등록번호가 본인인증 정보와 일치하지 않습니다.");
    }

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

    private void validateImageFile(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("이미지 파일이 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("이미지 파일만 업로드 가능합니다.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("파일 크기는 5MB 이하만 가능합니다.");
        }
    }
}
