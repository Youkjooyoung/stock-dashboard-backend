package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.base.url}")          private String appBaseUrl;
    @Value("${kakao.client-id}")      private String kakaoClientId;
    @Value("${kakao.redirect-uri}")   private String kakaoRedirectUri;
    @Value("${google.client-id}")     private String googleClientId;
    @Value("${google.client-secret}") private String googleClientSecret;
    @Value("${google.redirect-uri}")  private String googleRedirectUri;

    // ===== CREATE =====

    public void signup(UserDto dto) throws Exception {
        validator.validateEmail(dto.getEmail());
        validator.validatePassword(dto.getPassword());
        validator.validateNickname(dto.getNickname());
        validator.validateResidentNo(dto.getResidentNo());
        validator.validatePhone(dto.getPhone());

        if (checkEmailExists(dto.getEmail()))
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");

        dto.setPassword(passwordEncoder.encode(dto.getPassword()));
        dto.setResidentNo(aesEncryptor.encrypt(dto.getResidentNo()));

        String token = UUID.randomUUID().toString();
        dto.setEmailVerifyToken(token);

        userDao.insertUser(dto);
        emailService.sendVerificationEmail(dto.getEmail(), token);
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

    public Map<String, String> login(UserDto dto) {
        validator.validateEmail(dto.getEmail());

        UserDto user = userDao.findByEmail(dto.getEmail());
        if (user == null)
            throw new RuntimeException("이메일 또는 비밀번호가 틀렸습니다.");
        if ("Y".equals(user.getAccountLocked()))
            throw new RuntimeException("로그인 시도 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요.");
        if (!"Y".equals(user.getEmailVerified()))
            throw new RuntimeException("이메일 인증이 필요합니다.");

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            userDao.updateLoginFail(dto.getEmail());
            int remaining = 5 - (user.getLoginFailCnt() + 1);
            if (remaining <= 0) throw new RuntimeException("로그인 시도 횟수 초과로 계정이 잠겼습니다.");
            throw new RuntimeException("비밀번호가 틀렸습니다. 남은 시도: " + remaining + "회");
        }

        userDao.resetLoginFail(dto.getEmail());
        return issueTokens(user);
    }

    public Map<String, String> kakaoLogin(String code) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "grant_type=authorization_code&client_id=%s&redirect_uri=%s&code=%s",
            kakaoClientId, kakaoRedirectUri, code
        );

        String kakaoToken = fetchAccessToken(client,
            "https://kauth.kakao.com/oauth/token", tokenBody);

        JsonNode userJson = fetchUserInfo(client,
            "https://kapi.kakao.com/v2/user/me", kakaoToken);

        String email    = userJson.path("kakao_account").path("email").asText();
        String nickname = userJson.path("properties").path("nickname").asText();

        if (email.isEmpty())    email    = "kakao_" + userJson.path("id").asText() + "@kakao.com";
        if (nickname.isEmpty()) nickname = "카카오 사용자";

        return processSocialLogin(email, nickname, "KAKAO_");
    }

    public Map<String, String> googleLogin(String code) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
            code, googleClientId, googleClientSecret, googleRedirectUri
        );

        String googleToken = fetchAccessToken(client,
            "https://oauth2.googleapis.com/token", tokenBody);

        JsonNode userJson = fetchUserInfo(client,
            "https://www.googleapis.com/oauth2/v2/userinfo", googleToken);

        String email    = userJson.path("email").asText();
        String nickname = userJson.path("name").asText();

        if (email.isEmpty())    email    = "google_" + userJson.path("id").asText() + "@google.com";
        if (nickname.isEmpty()) nickname = "구글 사용자";

        return processSocialLogin(email, nickname, "GOOGLE_");
    }

    public void linkGoogle(String token, String code) throws Exception {
        UserDto user = getUserFromToken(token);
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
            code, googleClientId, googleClientSecret, appBaseUrl + "/oauth/link"
        );

        String googleToken = fetchAccessToken(client, "https://oauth2.googleapis.com/token", tokenBody);
        JsonNode userJson  = fetchUserInfo(client, "https://www.googleapis.com/oauth2/v2/userinfo", googleToken);

        String email = userJson.path("email").asText();
        if (email.isEmpty()) email = "google_" + userJson.path("id").asText() + "@google.com";

        UserSocialDto dto = new UserSocialDto();
        dto.setUserId(user.getUserId());
        dto.setProvider("GOOGLE");
        dto.setProviderEmail(email);
        userSocialDao.insertSocial(dto);
    }

    public void linkKakao(String token, String code) throws Exception {
        UserDto user = getUserFromToken(token);
        HttpClient client = HttpClient.newHttpClient();

        String tokenBody = String.format(
            "grant_type=authorization_code&client_id=%s&redirect_uri=%s&code=%s",
            kakaoClientId, appBaseUrl + "/oauth/link", code
        );

        String kakaoToken = fetchAccessToken(client, "https://kauth.kakao.com/oauth/token", tokenBody);
        JsonNode userJson = fetchUserInfo(client, "https://kapi.kakao.com/v2/user/me", kakaoToken);

        String email = userJson.path("kakao_account").path("email").asText();
        if (email.isEmpty()) email = "kakao_" + userJson.path("id").asText() + "@kakao.com";

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
        return Map.of("accessToken",
            jwtUtil.generateAccessToken(jwtUtil.getEmailFromRefresh(refreshToken)));
    }

    public void logout(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) return;
        UserDto user = userDao.findByEmail(jwtUtil.getEmailFromRefresh(refreshToken));
        if (user != null) refreshTokenDao.deleteByUserId(user.getUserId());
    }

    public void addWatchlist(String token, int itemId) {
        userDao.insertWatchlist(getUserFromToken(token).getUserId(), itemId);
    }

    public Map<String, String> verifyCertification(String impUid) throws Exception {
        return portoneService.getCertification(impUid);
    }

    public boolean verifyEmail(String token) {
        UserDto user = userDao.findByEmailVerifyToken(token);
        if (user == null) return false;
        userDao.updateEmailVerified(token);
        return true;
    }

    // ===== READ =====

    public boolean checkEmailExists(String email) {
        return userDao.checkEmailExists(email) > 0;
    }

    public boolean checkNicknameExists(String nickname) {
        return userDao.checkNicknameExists(nickname) > 0;
    }

    public UserDto findByEmail(String email) {
        return userDao.findByEmail(email);
    }

    public List<UserSocialDto> getSocialLinks(String token) {
        return userSocialDao.selectByUserId(getUserFromToken(token).getUserId());
    }

    public Map<String, String> getUserInfo(String token) {
        UserDto user = getUserFromToken(token);
        Map<String, String> info = new HashMap<>();
        info.put("email", user.getEmail());
        String dateStr = "";
        if (user.getCreatedAt() != null) {
            dateStr = new SimpleDateFormat("yyyy-MM-dd").format(user.getCreatedAt());
        }
        info.put("createdAt", dateStr);
        return info;
    }

    public List<Integer> getWatchlist(String token) {
        return userDao.selectWatchlist(getUserFromToken(token).getUserId());
    }

    public List<StockPriceDto> getWatchlistDetail(String token) {
        List<Integer> itemIds = userDao.selectWatchlist(getUserFromToken(token).getUserId());
        if (itemIds.isEmpty()) return new ArrayList<>();
        return stockDao.selectLatestPricesByItemIds(itemIds);
    }

    public void saveSocialLink(String token, UserSocialDto dto) {
        dto.setUserId(getUserFromToken(token).getUserId());
        dto.setProvider(dto.getProvider().toUpperCase());
        userSocialDao.insertSocial(dto);
    }

    // ===== UPDATE =====

    public void changePassword(String token, String currentPassword, String newPassword) {
        validator.validatePassword(newPassword);
        UserDto user = getUserFromToken(token);
        if (!passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new RuntimeException("현재 비밀번호가 틀렸습니다.");
        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updatePassword(user);
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

    public void updateNickname(String token, String nickname) {
        validator.validateNickname(nickname);
        UserDto user = getUserFromToken(token);
        user.setNickname(nickname);
        userDao.updateNickname(user);
    }

    // ===== DELETE =====

    public void deleteAccount(String token) {
        UserDto user = getUserFromToken(token);
        refreshTokenDao.deleteByUserId(user.getUserId());
        userDao.deleteUser(user.getUserId());
    }

    public void removeWatchlist(String token, int itemId) {
        userDao.deleteWatchlist(getUserFromToken(token).getUserId(), itemId);
    }

    public void unlinkSocial(String token, String provider) {
        userSocialDao.deleteSocial(getUserFromToken(token).getUserId(), provider.toUpperCase());
    }

    // ===== Private Helpers =====

    private String fetchAccessToken(HttpClient client, String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(res.body()).path("access_token").asText();
    }

    private JsonNode fetchUserInfo(HttpClient client, String url, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(res.body());
    }

    private UserDto getUserFromToken(String token) {
        return userDao.findByEmail(jwtUtil.getEmailFromAccess(token));
    }

    private Map<String, String> issueTokens(UserDto user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getEmail());
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
        tokens.put("email",    email);
        tokens.put("nickname", nickname);
        return tokens;
    }
}