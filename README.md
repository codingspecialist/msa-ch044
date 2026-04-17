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
Ingress Controller (path 기반 라우팅, minikube tunnel → http://localhost)
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

### 1. 사전 조건 확인 (Windows 기준)
- Docker Desktop 실행 중
- minikube · kubectl 설치 (`minikube version`, `kubectl version --client`)
- Python 3 설치 (`py --version`) — 프론트엔드 정적 서버용
- **관리자 권한 PowerShell 필요 없음** (hosts 파일 안 건드림)

> macOS / Linux 사용자도 동일한 흐름. Python은 `python3 --version`.

### 2. 미니큐브 & 이미지 빌드
```powershell
# Windows PowerShell — 일반 권한, chap044 폴더 루트에서
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
```powershell
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

### 4. Ingress 노출 — `minikube tunnel`
```powershell
# 새 PowerShell 창을 하나 더 띄워 거기서 실행 (포어그라운드 유지)
minikube tunnel
# - ingress-nginx LoadBalancer를 호스트의 127.0.0.1:80 에 매핑
# - Windows: 일반 권한이면 보통 동작 (UAC 뜨면 허용)
# - macOS/Linux: sudo 권한을 물을 수 있음
# - 이 창을 닫으면 외부 접근 끊김 → 세션 동안 켜둘 것
```

검증:
```powershell
curl.exe http://localhost/api/products    # 상품 목록 JSON 반환되면 OK
```

### 5. 프론트엔드 실행 (클러스터 외부)
옵션 A — **로컬 Python 정적 서버** (가장 간단)
```powershell
# Windows PowerShell — chap044 폴더 루트에서
cd frontend
py -m http.server 3000
# 브라우저: http://localhost:3000
```
```bash
# macOS / Linux
cd frontend
python3 -m http.server 3000
```

옵션 B — **Nginx 컨테이너로 로컬 실행**
```bash
docker build -t chap044-frontend ./frontend
docker run --rm -p 3000:80 chap044-frontend
```

옵션 C — **CDN 업로드** (실무)
Netlify / Vercel / S3+CloudFront 등에 `frontend/index.html` 업로드.

### 6. 브라우저 사용
1. `http://localhost:3000` 접속
2. 화면 최상단 **API 서버** 입력란에 `http://localhost` 입력 후 저장
3. 로그인 (`ssar`/`1234`) → WebSocket 연결 → 주문

### 7. 테스트 계정
- `ssar` / `1234` (id=1)
- `cos`  / `1234` (id=2)
- `love` / `1234` (id=3)

### 8. 테스트 시나리오
- **정상:** MacBook Pro (id=1) 주문 → 재고 차감 → 배송 생성 → 관리자가 `PUT /api/deliveries/{id}/complete` → 웹소켓 푸시
- **실패 (재고 부족):** iPhone 15 (id=2, 재고 0) 주문 → 재고 부족 → Saga 보상으로 주문 CANCELLED

---

## 핵심 포인트
- **외부 진입점은 Ingress 하나**. NodePort 안 씀 (Ingress 컨트롤러는 `minikube tunnel`로 `127.0.0.1:80`에 노출)
- **Ingress 라우팅은 path 기반**. host 헤더는 보지 않음 (`Host: *`) → hosts 파일 수정 불필요
- **JWT 검증은 order-service 내부** (`JwtVerifier` + `OrderController.authenticate()`)에서만. 게이트웨이 인증 없음
- **프론트엔드는 정적 자원**. 백엔드와 별개 라이프사이클로 배포 가능 (CDN 권장)
- **CORS는 Ingress annotation**에서 일괄 처리 → 모든 백엔드 서비스가 CORS 코드 없이 동작
- **DB 포트는 3307** (3306은 호스트 로컬 MySQL과 충돌 우려 → 클러스터 내부도 3307로 통일)

## 참고 파일
- `msa04.md` — 개념 해설서
- `simulator-msa-04.html` — 브라우저에서 열어보는 Saga 시뮬레이터
