-- V20261015_unique_social_provider_email.sql 되돌리기. 데이터는 지우지 않는다.
ALTER TABLE USER_SOCIAL DROP INDEX uq_social_provider_email;
