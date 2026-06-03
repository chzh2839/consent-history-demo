-- 테스트용 사용자 데이터
INSERT INTO users (id, username, email, created_at) VALUES
    (1, 'user_alice', 'alice@example.com', NOW()),
    (2, 'user_bob',   'bob@example.com',   NOW());

-- 약관 마스터 데이터
INSERT INTO terms (id, terms_code, terms_name, is_mandatory, created_at) VALUES
    (1, 'TERMS_OF_SERVICE',  '서비스 이용약관',   true,  NOW()),
    (2, 'PRIVACY_POLICY',    '개인정보 처리방침', true,  NOW()),
    (3, 'MARKETING_CONSENT', '마케팅 수신 동의',  false, NOW());

-- 약관 버전 데이터
INSERT INTO terms_version (id, terms_id, version, content, is_active, effective_date, created_at) VALUES
    (1, 1, 'v1.0', '서비스 이용약관 내용 (v1.0) - 초기 버전',         false, '2024-01-01 00:00:00', NOW()),
    (2, 1, 'v1.1', '서비스 이용약관 내용 (v1.1) - 개정판',             false, '2024-06-01 00:00:00', NOW()),
    (3, 1, 'v2.0', '서비스 이용약관 내용 (v2.0) - 현행 최신 버전',     true,  '2025-01-01 00:00:00', NOW()),
    (4, 2, 'v1.0', '개인정보 처리방침 내용 (v1.0) - 초기 버전',        false, '2024-01-01 00:00:00', NOW()),
    (5, 2, 'v2.0', '개인정보 처리방침 내용 (v2.0) - 현행 최신 버전',   true,  '2025-01-01 00:00:00', NOW()),
    (6, 3, 'v1.0', '마케팅 수신 동의 내용 (v1.0) - 현행 최신 버전',    true,  '2024-01-01 00:00:00', NOW());
