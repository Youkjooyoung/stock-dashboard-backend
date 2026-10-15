package com.stock.dashboard.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CertificationUseDao {
	/** imp_uid 가 이미 기록돼 있으면 PK 중복으로 DuplicateKeyException 이 난다. */
	int insertUse(@Param("impUid") String impUid, @Param("email") String email, @Param("purpose") String purpose);
}
