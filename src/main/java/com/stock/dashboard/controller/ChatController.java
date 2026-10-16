package com.stock.dashboard.controller;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dao.ChatDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.ChatMessageDto;
import com.stock.dashboard.dto.UserDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 종목별 토론방. 지난 대화는 REST(/api/chat/{ticker})로, 새 메시지는 웹소켓(/app/chat/{ticker})으로 받는다. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final int    HISTORY_LIMIT      = 50;
    private static final int    MAX_CONTENT_LENGTH = 200;
    private static final String TICKER_PATTERN     = "^[0-9A-Z]{6,12}$";

    private final ChatDao               chatDao;
    private final UserDao               userDao;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/api/chat/{ticker}")
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable String ticker) {
        if (!ticker.matches(TICKER_PATTERN)) {
            throw new IllegalArgumentException("종목코드 형식이 올바르지 않습니다.");
        }
        return ResponseEntity.ok(chatDao.selectRecentMessages(ticker, HISTORY_LIMIT));
    }

    /**
     * 보낸 사람은 클라이언트가 보낸 값이 아니라 웹소켓 접속 때 인증된 사용자(principal)로 정한다.
     * 닉네임도 DB 값을 써서 "관리자" 같은 닉네임 위조를 막는다.
     */
    @MessageMapping("/chat/{ticker}")
    public void sendMessage(
            @DestinationVariable String ticker,
            @Payload Map<String, String> payload,
            Principal principal) {
        if (principal == null || !ticker.matches(TICKER_PATTERN)) return;

        String content = payload.getOrDefault("content", "").trim();
        if (content.isEmpty()) return;
        if (content.length() > MAX_CONTENT_LENGTH) content = content.substring(0, MAX_CONTENT_LENGTH);

        UserDto user = userDao.findByEmail(principal.getName());
        if (user == null) return;

        ChatMessageDto msg = new ChatMessageDto();
        msg.setTicker(ticker);
        msg.setUserEmail(user.getEmail());
        msg.setSenderId(user.getUserId());
        msg.setNickname(user.getNickname() != null ? user.getNickname() : "회원" + user.getUserId());
        msg.setContent(content);

        chatDao.insertMessage(msg);
        msg.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        messagingTemplate.convertAndSend("/topic/chat/" + ticker, msg);
        log.debug("채팅 전송: [{}] senderId={}", ticker, user.getUserId());
    }
}
