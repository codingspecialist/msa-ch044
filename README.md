# Chapter 4.4 — K8s · MSA · WebSocket 교육 예제 (슬림 + 멱등성 버전)

## 폴더 구조
```
chap044/
├── db/            # MySQL init
├── user/          # 로그인 · JWT 발급
├── product/       # 상품 · 재고 (Kafka Consumer + 멱등성)
├── order/         # 주문 · WebSocket (Kafka Consumer + 멱등성 + JWT)
├── delivery/      # 배송 (Kafka Consumer + 멱등성)
├── orchestrator/  # Saga 중앙 조율자 (결정적 messageId)
├── gateway/       # Nginx reverse proxy
├── frontend/      # 정적 HTML + STOMP client
├── k8s/           # Kubernetes manifests
├── book.md                   # 책
└── simulator-msa-ch04.html   # 인터랙티브 시뮬레이터
```

## 서비스 · 토픽 일관 네이밍
- **Command** (명령): `<entity>-<verb>-command`  (예: `product-decrease-command`)
- **Event** (완료): `<entity>-<past-verb>-event` (예: `product-decreased-event`)

## 실행 단계

### 1. 미니큐브 & 이미지 빌드
```bash
minikube start

minikube image build -t metacoding/db:1 ./db
minikube image build -t metacoding/user:1 ./user
minikube image build -t metacoding/product:1 ./product
minikube image build -t metacoding/order:1 ./order
minikube image build -t metacoding/delivery:1 ./delivery
minikube image build -t metacoding/orchestrator:1 ./orchestrator
minikube image build -t metacoding/gateway:1 ./gateway
minikube image build -t metacoding/frontend:1 ./frontend
```

### 2. 배포
```bash
kubectl create namespace metacoding

kubectl apply -f k8s/kafka
kubectl wait --for=condition=ready pod -l app=kafka -n metacoding --timeout=120s

kubectl apply -f k8s/db
kubectl apply -f k8s/user
kubectl apply -f k8s/product
kubectl apply -f k8s/order
kubectl apply -f k8s/delivery
kubectl apply -f k8s/orchestrator
kubectl apply -f k8s/gateway
kubectl apply -f k8s/frontend
```

### 3. 접속
```bash
minikube service frontend-service -n metacoding --url
```

### 4. 테스트 계정
- `ssar` / `1234` (id=1)
- `cos`  / `1234` (id=2)
- `love` / `1234` (id=3)

### 5. 테스트 시나리오
- **정상:** MacBook Pro (id=1) 주문 → 재고 차감 → 배송 생성 → 관리자가 `PUT /api/deliveries/{id}/complete` → 웹소켓 푸시
- **실패 (재고 부족):** iPhone 15 (id=2, 재고 0) 주문 → 재고 부족 → Saga 보상으로 주문 CANCELLED

## 참고 파일
- `book.md` — 개념 해설서
- `simulator-msa-ch04.html` — 브라우저에서 열어보는 Saga 시뮬레이터
