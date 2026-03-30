package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stock.dashboard.dto.ChatMessageDto;

@Mapper
public interface ChatDao {
	int insertMessage(ChatMessageDto dto);

	List<ChatMessageDto> selectRecentMessages(@Param("ticker") String ticker, @Param("limit") int limit);
}