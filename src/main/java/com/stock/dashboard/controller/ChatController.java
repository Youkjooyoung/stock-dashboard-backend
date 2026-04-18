package com.stock.dashboard.controller;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import com.stock.dashboard.JwtUtil;
import com.stock.dashboard.dao.ChatDao;
import com.stock.dashboard.dto.ChatMessageDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatDao              chatDao;
    private final JwtUtil              jwtUtil;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/api/chat/{ticker}")
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable String ticker) {
        return ResponseEntity.ok(chatDao.selectRecentMessages(ticker, 50));
    }

    @MessageMapping("/chat/{ticker}")
    public void sendMessage(
            @DestinationVariable String ticker,
            @Payload Map<String, String> payload,
            @Header("Authorization") String token) {
        try {
            String email    = jwtUtil.getEmailFromAccess(token.replace("Bearer ", ""));
            String nickname = payload.getOrDefault("nickname", email.split("@")[0]);
            String content  = payload.getOrDefault("content", "").trim();

            if (content.isEmpty()) return;

            ChatMessageDto msg = new ChatMessageDto();
            msg.setTicker(ticker);
            msg.setUserEmail(email);
            msg.setNickname(nickname);
            msg.setContent(content);

            chatDao.insertMessage(msg);
            msg.setCreatedAt(new Date());
            messagingTemplate.convertAndSend("/topic/chat/" + ticker, msg);
            log.debug("채팅 전송: [{}] {} - {}", ticker, nickname, content);

        } catch (Exception e) {
            log.warn("채팅 오류: {}", e.getMessage());
        }
    }
}