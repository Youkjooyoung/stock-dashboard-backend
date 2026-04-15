package com.stock.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.stock.dashboard.dao.PriceAlertDao;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.PriceAlertDto;
import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.dto.UserDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertDao         alertDao;
    private final SimpMessagingTemplate messagingTemplate;
    private final StockDao              stockDao;
    private final UserDao               userDao;

    public void addAlert(String email, PriceAlertDto dto) {
        UserDto user = getUser(email);
        dto.setUserId(user.getUserId());
        alertDao.insertAlert(dto);
    }

    public void checkAlerts() {
        List<PriceAlertDto> alerts = alertDao.selectActiveAlerts();
        if (alerts.isEmpty()) return;

        for (PriceAlertDto alert : alerts) {
            try {
                List<StockPriceDto> prices = stockDao.selectPriceByTicker(alert.getTicker());
                if (prices.isEmpty()) continue;

                long currentPrice = prices.get(0).getClpr();
                boolean triggered =
                    ("ABOVE".equals(alert.getAlertType()) && currentPrice >= alert.getTargetPrice()) ||
                    ("BELOW".equals(alert.getAlertType()) && currentPrice <= alert.getTargetPrice());

                if (triggered) {
                    alertDao.triggerAlert(alert.getAlertId());
                    sendAlert("/topic/alert/" + alert.getUserId(), Map.of(
                        "type",         "PRICE_ALERT",
                        "ticker",       alert.getTicker(),
                        "stockName",    alert.getStockName(),
                        "targetPrice",  alert.getTargetPrice(),
                        "currentPrice", currentPrice,
                        "alertType",    alert.getAlertType()
                    ));
                    log.info("[목표가 도달] {} {} {}원 → {}원",
                        alert.getStockName(), alert.getAlertType(),
                        alert.getTargetPrice(), currentPrice);
                }
            } catch (Exception e) {
                log.warn("[알림 체크 오류] {}", e.getMessage());
            }
        }
    }

    public void checkBigMoveStocks() {
        try {
            for (StockPriceDto stock : stockDao.selectLatestPrices()) {
                long open  = stock.getMkp();
                long close = stock.getClpr();
                if (open <= 0) continue;

                double rate = ((double)(close - open) / open) * 100;
                if (Math.abs(rate) < 10) continue;

                String direction     = rate > 0 ? "급등" : "급락";
                String rateFormatted = String.format("%.2f", rate);

                log.info("[급등락] {} {} {}%", stock.getItmsNm(), direction, rateFormatted);

                Map<String, Object> notification = Map.of(
                    "type",      "BIG_MOVE",
                    "ticker",    stock.getSrtnCd(),
                    "stockName", stock.getItmsNm(),
                    "rate",      rateFormatted,
                    "direction", direction,
                    "close",     close
                );

                userDao.selectUserIdsByWatchTicker(stock.getSrtnCd())
                       .forEach(uid -> sendAlert("/topic/alert/" + uid, notification));
            }
        } catch (Exception e) {
            log.warn("[급등락 알림 오류] {}", e.getMessage());
        }
    }

    public void deleteAlert(String email, int alertId) {
        UserDto user = getUser(email);
        alertDao.deleteAlert(alertId, user.getUserId());
    }

    public List<PriceAlertDto> getMyAlerts(String email) {
        UserDto user = getUser(email);
        return alertDao.selectByUserId(user.getUserId());
    }

    private UserDto getUser(String email) {
        return userDao.findByEmail(email);
    }

    private void sendAlert(String destination, Map<String, Object> payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}