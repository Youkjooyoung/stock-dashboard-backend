---
name: deploy-backend
description: 백엔드 운영 배포 전 상태 확인 및 배포 명령어 안내
disable-model-invocation: true
---

백엔드 운영 배포를 준비한다. 다음 순서로 진행해:

1. 현재 브랜치와 미커밋 변경사항 확인 (git status, git branch)
2. develop → main 머지 여부 확인 (git log --oneline -5)
3. 미커밋 파일이 있으면 커밋 먼저 할지 사용자에게 확인
4. 아래 배포 명령어를 출력하고 MobaXterm에서 실행하도록 안내:

```bash
cd ~/stock-dashboard-backend
git pull origin main
./mvnw clean package -DskipTests
sudo systemctl restart stock-dashboard
```

5. 배포 후 확인 방법 안내:
   - `sudo systemctl status stock-dashboard` 로 서비스 상태 확인
   - `https://api.jyyouk.shop/api/stock/prices` 로 API 응답 확인
