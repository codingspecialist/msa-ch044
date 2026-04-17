# Chapter 4.4 — K8s · MSA · WebSocket 교육 예제 (슬림 + 멱등성 버전)

## 아키텍처 변경점 (v2)
- **Frontend는 클러스터 밖**에서 실행 (CDN/로컬 static server 권장)
- **Gateway(Nginx) 제거**. Ingress가 직접 백엔드 path 라우팅
- CORS는 Ingress annotation 한 곳에서 통합 처리

```
[외부]
Browser (정적 HTML on CDN/로컬)
   │  fetch / SockJS  (CORS)
   ▼
[클러스터]
Ingress Controller (host: metacoding.local)
   ├─ /api/login      → user-service:8083
   ├─ /api/products   → product-service:8082
   ├─ /api/orders     → order-service:8081   (JWT 검증)
   ├─ /api/deliveries → delivery-service:8084
   └─ /ws             → order-service:8081   (SockJS/STOMP)
```

## 폴더 구조
```
chap044/
├── db/            # MySQL init
├── user/          # 로그인 · JWT 발급
├── product/       # 상품 · 재고 (Kafka Consumer + 멱등성)
├── order/         # 주문 · WebSocket (Kafka Consumer + 멱등성 + JWT 검증)
├── delivery/      # 배송 (Kafka Consumer + 멱등성)
├── orchestrator/  # Saga 중앙 조율자 (결정적 messageId)
├── frontend/      # 정적 HTML + STOMP client  ※ 외부에서 실행
├── k8s/           # Kubernetes manifests (ingress, 각 서비스)
├── msa04.md                     # 책
└── simulator-msa-04.html        # 인터랙티브 시뮬레이터
```

## 서비스 · 토픽 일관 네이밍
- **Command** (명령): `<entity>-<verb>-command`  (예: `product-decrease-command`)
- **Event** (완료): `<entity>-<past-verb>-event` (예: `product-decreased-event`)

---

## 실행 단계

### 1. 호스트 매핑 (최초 1회)
```bash
# macOS/Linux
echo "$(minikube ip) metacoding.local" | sudo tee -a /etc/hosts

# Windows (관리자 PowerShell)
# Add-Content C:\Windows\System32\drivers\etc\hosts "$(minikube ip) metacoding.local"
```

### 2. 미니큐브 & 이미지 빌드
```bash
minikube start --addons=ingress     # ingress-nginx 활성화

minikube image build -t metacoding/db:1 ./db
minikube image build -t metacoding/user:1 ./user
minikube image build -t metacoding/product:1 ./product
minikube image build -t metacoding/order:1 ./order
minikube image build -t metacoding/delivery:1 ./delivery
minikube image build -t metacoding/orchestrator:1 ./orchestrator
# ※ frontend는 클러스터 빌드 대상 아님 (외부에서 실행)
# ※ gateway 폴더는 삭제됨 (Ingress가 직접 라우팅)
```

### 3. 백엔드 배포
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
kubectl apply -f k8s/ingress
```

### 4. 프론트엔드 실행 (클러스터 외부)
옵션 A — **로컬 Python 정적 서버**
```bash
cd frontend
python3 -m http.server 3000
# 브라우저: http://localhost:3000
```

옵션 B — **Nginx 컨테이너로 로컬 실행**
```bash
docker build -t chap044-frontend ./frontend
docker run --rm -p 3000:80 chap044-frontend
```

옵션 C — **CDN 업로드** (실무)
Netlify / Vercel / S3+CloudFront 등에 `frontend/index.html` 업로드.

### 5. 브라우저 사용
1. `http://localhost:3000` 접속
2. 화면 최상단 **API 서버** 입력란에 `http://metacoding.local` 확인 (필요시 수정 후 저장)
3. 로그인 (`ssar`/`1234`) → WebSocket 연결 → 주문

### 6. 테스트 계정
- `ssar` / `1234` (id=1)
- `cos`  / `1234` (id=2)
- `love` / `1234` (id=3)

### 7. 테스트 시나리오
- **정상:** MacBook Pro (id=1) 주문 → 재고 차감 → 배송 생성 → 관리자가 `PUT /api/deliveries/{id}/complete` → 웹소켓 푸시
- **실패 (재고 부족):** iPhone 15 (id=2, 재고 0) 주문 → 재고 부족 → Saga 보상으로 주문 CANCELLED

---

## 핵심 포인트
- **외부 진입점은 Ingress 하나**. NodePort/LoadBalancer 안 씀
- **JWT 검증은 order-service 내부** (`JwtVerifier` + `OrderController.authenticate()`)에서만. 게이트웨이 인증 없음
- **프론트엔드는 정적 자원**. 백엔드와 별개 라이프사이클로 배포 가능 (CDN 권장)
- **CORS는 Ingress annotation**에서 일괄 처리 → 모든 백엔드 서비스가 CORS 코드 없이 동작

## 참고 파일
- `msa04.md` — 개념 해설서
- `simulator-msa-04.html` — 브라우저에서 열어보는 Saga 시뮬레이터
