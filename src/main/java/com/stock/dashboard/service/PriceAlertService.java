package com.stock.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.stock.dashboard.dao.PriceAlertDao;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.PriceAlertDto;
import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 목표가 알림과 급등락 알림. 조건을 만족하면 그 사용자에게만 웹소켓으로 보낸다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private static final double BIG_MOVE_PERCENT = 10;

    private final PriceAlertDao         alertDao;
    private final SimpMessagingTemplate messagingTemplate;
    private final StockDao              stockDao;
    private final UserDao               userDao;

    public void addAlert(String email, PriceAlertDto dto) {
        UserDto user = getUser(email);
        dto.setUserId(user.getUserId());
        alertDao.insertAlert(dto);
    }

    /**
     * 아직 울리지 않은 목표가 알림을 모두 검사한다(StockScheduler 가 1분마다 호출).
     * 최신 종가가 목표가 이상(ABOVE)/이하(BELOW)가 되면 한 번만 울리고 IS_TRIGGERED 를 'Y' 로 바꾼다.
     * 알림 하나가 실패해도 나머지는 계속 검사한다.
     */
    public void checkAlerts() {
        try {
            List<PriceAlertDto> alerts = alertDao.selectActiveAlerts();
            if (alerts.isEmpty()) return;

            for (PriceAlertDto alert : alerts) {
                try {
                    // 날짜 내림차순이라 첫 행이 가장 최근 종가다. (종목 전체 이력을 읽으므로 고도화 목록 14번에서 개선 예정)
                    List<StockPriceDto> prices = stockDao.selectPriceByTicker(alert.getTicker());
                    if (prices.isEmpty()) continue;

                    long currentPrice = prices.get(0).getClpr();
                    boolean triggered =
                        ("ABOVE".equals(alert.getAlertType()) && currentPrice >= alert.getTargetPrice()) ||
                        ("BELOW".equals(alert.getAlertType()) && currentPrice <= alert.getTargetPrice());

                    if (triggered) {
                        alertDao.triggerAlert(alert.getAlertId());
                        sendAlert(alert.getUserEmail(), Map.of(
                            "type",         "PRICE_ALERT",
                            "ticker",       alert.getTicker(),
                            "stockName",    alert.getStockName(),
                            "targetPrice",  alert.getTargetPrice(),
                            "currentPrice", currentPrice,
                            "alertType",    alert.getAlertType()
                        ));
                        log.info("[price-alert] triggered stockName={}, type={}, targetPrice={}, currentPrice={}",
                            alert.getStockName(), alert.getAlertType(),
                            alert.getTargetPrice(), currentPrice);
                    }
                } catch (Exception e) {
                    log.warn("[price-alert] failed to process alertId={}: {}", alert.getAlertId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[price-alert] failed to load active alerts: {}", e.getMessage());
        }
    }

    /**
     * 장 마감 뒤(평일 16:30) 최신 시세에서 시가 대비 종가가 ±10% 이상 움직인 종목을 찾아,
     * 그 종목을 관심 종목으로 등록한 사용자에게 알린다.
     */
    public void checkBigMoveStocks() {
        try {
            for (StockPriceDto stock : stockDao.selectLatestPrices()) {
                long open  = stock.getMkp();
                long close = stock.getClpr();
                if (open <= 0) continue;

                double rate = ((double)(close - open) / open) * 100;
                if (Math.abs(rate) < BIG_MOVE_PERCENT) continue;

                String direction     = rate > 0 ? "UP" : "DOWN";
                String rateFormatted = String.format("%.2f", rate);

                log.info("[big-move] {} {} {}%", stock.getItmsNm(), direction, rateFormatted);

                Map<String, Object> notification = Map.of(
                    "type",      "BIG_MOVE",
                    "ticker",    stock.getSrtnCd(),
                    "stockName", stock.getItmsNm(),
                    "rate",      rateFormatted,
                    "direction", direction,
                    "close",     close
                );

                userDao.selectUserEmailsByWatchTicker(stock.getSrtnCd())
                       .forEach(email -> sendAlert(email, notification));
            }
        } catch (Exception e) {
            log.warn("[big-move] failed to check stocks: {}", e.getMessage());
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
        UserDto user = email != null ? userDao.findByEmail(email) : null;
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다. 다시 로그인해주세요.");
        return user;
    }

    // 본인의 웹소켓 세션(/user/queue/alert)에만 전달된다. 예전 /topic/alert/{userId} 는 누구나 구독할 수 있었다.
    private void sendAlert(String email, Map<String, Object> payload) {
        messagingTemplate.convertAndSendToUser(email, "/queue/alert", payload);
    }
}
