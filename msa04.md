# 메타몰 상세판 — 네 점포 이야기에 숨긴 다섯 가지 진실

> *카프카·쿠버네티스·사가 오케스트레이터·멱등성·마이크로서비스,
> 한 권으로 완전히 뚫어보기*

---

## 목차

- **프롤로그 — 0.3초의 침묵**
- **1장. 점포를 넷으로 나눈 이유** (마이크로서비스의 탄생)
- **2장. 관제탑과 터널** (쿠버네티스 간략 지도)
- **3장. 손도장 하나로 통한다** (JWT 토큰)
- **4장. 전령 대신 방송실을 두기로** (카프카 심화 1 — 기본 원리)
- **5장. 명령장과 소식지** (카프카 심화 2 — 토픽 설계)
- **6장. 지휘자의 등장** (사가 심화 1 — 오케스트레이션)
- **7장. 지휘가 무너질 때** (사가 심화 2 — 실패와 보상)
- **8장. 같은 전갈을 두 번 받으면** (멱등성 심화)
- **9장. 명령과 이벤트, 왜 멱등 처리가 다른가**
- **10장. 고객의 어깨 위의 작은 새** (웹소켓)
- **11장. 전체를 한 장면으로** (통합 시나리오)
- **12장. 그림자극으로 다시 보기** (시뮬레이터 가이드)
- **13장. 프로덕션으로 가려면** (에필로그 겸 심화)
- **부록 A. 토픽·메시지 전수표**
- **부록 B. DB 스키마 ERD**
- **부록 C. 용어집**

---

## 이 책의 약속

이 책은 세 가지 약속을 하면서 시작한다.

첫째, **모든 기술 개념은 두 번 설명한다.** 한 번은 "점포", "관제탑", "지휘자" 같은 쉬운 비유로, 한 번은 정확한 기술 용어와 작동 원리로. 비유만으로는 실전을 견디지 못하고, 개념만으로는 기억에 남지 않는다. 둘은 서로를 버팀목 삼아야 한다.

둘째, **소스코드는 꼭 필요한 곳에서만 보여준다.** 많은 책이 코드로 지면을 채우지만, 이 책은 **여덟 개의 핵심 스냅샷**만 남기고 나머지는 표·다이어그램·의사코드로 설명한다. 구조를 이해하는 데 코드 300줄이 필요한 경우는 거의 없다.

셋째, **같은 예제를 계속 우려낸다.** 처음부터 끝까지 "MacBook Pro 한 대를 주문하는 사용자 ssar"이라는 한 장면으로 열세 가지 주제를 엮는다. 예제가 바뀌면 머리도 같이 초기화되기 때문이다.

이 책이 다루는 소스코드는 모두 `chap044/` 폴더 안에 실제로 동작하는 코드로 들어 있다. 책을 읽고 실행해보고 책을 다시 읽으면, 둘째 읽을 때는 이미 당신은 이 분야를 **설명할 수 있는 사람**이 되어 있을 것이다.

---

# 프롤로그 — 0.3초의 침묵

## 0.3초

사용자가 "주문하기" 버튼을 누른다. 화면 한가운데 작은 스피너가 돈다. 스피너가 도는 시간은 길어야 0.3초다. 대부분의 사용자는 이 시간을 "그냥 로딩"이라고 부른다.

그러나 그 0.3초 안에서 일어나는 일들은 이렇다.

- 브라우저는 먼저 HTTP 요청을 `/api/orders`로 쏜다. 그 요청은 먼저 **Nginx 게이트웨이**에 닿는다.
- 게이트웨이는 요청 헤더의 `Authorization: Bearer …`를 확인하고 **Order 서비스**로 전달한다.
- Order 서비스는 JWT 토큰을 복호화해 "아, 이 요청은 userId 1번의 것이구나"를 확인한다.
- Order 서비스는 자신의 DB에 `INSERT INTO order_tb`를 실행한다. 이때 주문 상태는 `PENDING`이다.
- Order 서비스는 **Kafka**라는 메시지 브로커에 `order-created-event`라는 이름의 메시지를 쏜다. 그리고 곧바로 사용자에게 "주문 접수 완료"라는 응답을 돌려준다.

여기까지가 0.3초다. 하지만 진짜 이야기는 이제부터다.

## 0.3초 뒤

사용자 화면에는 "주문 접수됨"이라는 글자가 나타난다. 스피너는 멈추었다. 그러나 백엔드에서는 다섯 개의 일이 **순서대로** 일어난다.

1. Kafka에 올라간 `order-created-event`를 **오케스트레이터**가 본다.
2. 오케스트레이터는 **Product 서비스**에게 "이 주문의 재고를 차감하라"는 명령을 보낸다 (`product-decrease-command`).
3. Product 서비스는 재고를 차감하고, "차감 완료"를 Kafka에 방송한다 (`product-decreased-event`).
4. 오케스트레이터는 그 방송을 듣고 **Delivery 서비스**에게 "배송을 준비하라"고 명령한다 (`delivery-create-command`).
5. Delivery 서비스는 배송 기록을 생성하고 "배송 준비 완료"를 방송한다 (`delivery-created-event`).

이 다섯 단계가 모두 성공하면, 사용자는 나중에 **웹소켓**을 통해 "주문이 완료되었습니다"라는 알림을 받는다. 이 모든 과정을 전부 합치면 대략 2초. 그러나 사용자가 체감한 것은 처음의 0.3초뿐이었다.

이것이 **마이크로서비스 아키텍처(MSA)**의 진짜 모습이다. 하나의 점포 대신 여러 점포가 일하고, 여러 점포가 편지(메시지)로 의사소통하며, 중앙의 지휘자가 순서를 정한다. 사용자는 그 복잡한 뒤편을 모른 채 결과만 받는다.

## 다섯 가지 질문

그러나 이 우아한 그림은 **하나라도 삐끗하면 금세 악몽**으로 변한다. 이 책은 그 악몽을 막기 위한 다섯 가지 질문에 답한다.

**질문 1.** 재고가 부족해서 차감이 실패했다. 그런데 이미 주문은 `PENDING` 상태로 저장되어 있다. 이 주문은 어떻게 되나?

**질문 2.** 배송 생성까지는 성공했는데 갑자기 서버가 죽었다. 재고는 이미 차감되어 있다. 누가 이걸 되돌리나?

**질문 3.** Kafka가 같은 명령을 두 번 전달했다. 재고가 두 번 차감되면 재고가 음수가 된다. 이걸 어떻게 막나?

**질문 4.** 오케스트레이터 서비스 자체가 죽었다. 절반쯤 진행된 주문들은 어떻게 되나?

**질문 5.** 이 모든 것을 설명하는 "사가 패턴"과 "멱등성"은 도대체 무엇인가?

이 다섯 가지 질문은 프롤로그의 마지막 문장이자 에필로그의 마지막 체크리스트다. 책의 마지막 장에서 우리는 이 질문들을 다시 만날 것이다. 그때 당신은 자신의 말로 각각에 답할 수 있어야 한다.

## 출발

이제 첫 번째 점포의 문을 연다.

---

# 1장. 점포를 넷으로 나눈 이유

## 1.1 비유로 보기 — 한 점포의 몰락과 네 점포의 탄생

옛날에 한 사장님이 있었다. 그는 혼자서 모든 일을 했다. 손님을 맞이하고, 상품을 포장하고, 배송 차량을 운전하고, 장부를 정리했다. 한동안은 잘 돌아갔다. 그러나 세 가지 문제가 순서대로 덮쳤다.

**첫째 문제: 바쁜 시간대.** 점심에 주문이 폭주하면 사장은 장부를 덮고 포장에만 매달렸다. 장부는 밀렸다. 배송은 늦었다. 불만이 쌓였다. 단 하나의 작업도 독립적으로 확장할 수 없었다. 주문이 열 배 늘면 사장 한 명으로는 막을 수 없는데, 그렇다고 쿠폰을 다루는 시스템까지 열 배 늘리는 것은 낭비였다.

**둘째 문제: 장애의 전파.** 어느 날 장부 장비가 고장났다. 사장은 장부를 못 쓰니 새 주문을 받는 것도 불안해졌다. 그래서 가게 전체 문을 닫았다. 주문도 못 받고, 배송도 못 하고, 환불도 못 했다. 하나의 부품이 전체를 끌고 내려갔다. 이것을 **전체 장애(Single Point of Failure)**라고 부른다.

**셋째 문제: 바뀌는 전문성.** 새 직원을 뽑으려고 면접을 봤다. 그러나 "장부도 잘 쓰고, 포장도 잘하고, 배송도 운전 가능한" 사람은 극히 드물었다. 뽑았다 해도 한 명이 네 가지를 유지하려면 훈련 비용이 너무 컸다.

사장은 결국 점포를 **넷으로 쪼갰다**. 손님 응대 점포, 상품·재고 점포, 주문 점포, 배송 점포. 각 점포에는 전문 점장이 있고, 점포마다 자기만의 노하우(데이터)가 있다. 점포들은 서로 모르지만 **편지**로 일을 주고받는다.

이 비유의 핵심은 네 가지다.
- 각 점포는 **하나의 일에만 전문**이다.
- 각 점포는 **독립적으로 바쁘면 바쁜 만큼 인원을 늘릴 수 있다**.
- 한 점포가 문을 닫아도 **다른 점포는 영업을 계속**할 수 있다.
- 점포 간 소통은 **반드시 편지(명확한 메시지)**로 한다. 손짓이나 큰 소리는 허용되지 않는다.

## 1.2 개념으로 정의하기 — 마이크로서비스 아키텍처

앞의 비유를 기술 언어로 옮기면 이렇게 된다.

### 모놀리식(Monolithic) 아키텍처란 무엇인가

하나의 애플리케이션 안에 **모든 기능**이 들어 있는 구조다. 로그인·상품·주문·배송·결제·리뷰가 한 코드베이스, 한 JAR 파일, 한 DB에 살아 있다. 스타트업 초기에는 이것이 가장 빠르고 단순하다. 한 명이 만들고 한 명이 운영할 수 있기 때문이다.

모놀리식의 장점은 명확하다.
- 트랜잭션 경계가 단순하다. `@Transactional` 한 줄로 모든 작업이 원자적이 된다.
- 배포가 단순하다. JAR 하나를 실행하면 끝이다.
- 디버깅이 쉽다. 스택 트레이스 한 줄로 원인을 추적할 수 있다.

그러나 시스템이 커지면 앞의 "한 점포의 몰락"과 동일한 세 가지 문제가 나타난다.
- **확장의 비효율**: 주문 트래픽이 폭증한다고 리뷰 기능까지 같이 복제해야 한다.
- **장애의 전파**: 리뷰 모듈 한 군데의 메모리 누수가 결제 모듈까지 끌어내린다.
- **팀 충돌**: 한 팀의 배포가 다른 팀의 코드를 의도치 않게 수정한다.

### 마이크로서비스(Microservices) 아키텍처란 무엇인가

애플리케이션을 **작고 독립적인 서비스들의 집합**으로 쪼갠 구조다. 각 서비스는:
- **자신의 책임 범위만 관리**한다 (Single Responsibility).
- **자신의 API(또는 메시지 인터페이스)**로만 외부와 통신한다.
- **독립적으로 개발·테스트·배포·확장**된다.
- 이상적으로는 **자신만의 데이터베이스**를 갖는다 (Database per Service).

MSA의 장점은 모놀리식의 약점을 정확히 반대로 뒤집는다.
- 주문 트래픽이 폭증해도 **주문 서비스만** 10배로 스케일 아웃한다.
- 리뷰 모듈이 죽어도 결제·주문은 **계속 동작**한다.
- 리뷰 팀과 결제 팀은 **각자 자기 리포지토리에서 따로 배포**한다.

### 마이크로서비스의 대가

그러나 MSA는 공짜가 아니다. 모놀리식에서 쉬웠던 것들이 갑자기 어려워진다.

| 주제 | 모놀리식 | MSA |
|---|---|---|
| 트랜잭션 | `@Transactional` 한 줄 | **사가 패턴** 필요 |
| 호출 | 메소드 호출 | 네트워크 호출 (HTTP/Kafka) |
| 디버깅 | 스택 트레이스 한 줄 | **분산 추적(tracing)** 필요 |
| 버전 관리 | 한 군데 배포 | 서비스 간 호환성 관리 |
| 테스트 | 유닛/통합 테스트 | **계약 테스트(contract test)** 필요 |

이 책이 다루는 핵심 주제인 **사가 오케스트레이터**와 **멱등성**은 "트랜잭션" 항목에서 발생한 빈자리를 메우기 위해 등장한 도구다. 즉 **MSA는 MSA만으로 완성되지 않는다**. 사가와 멱등성이 없으면 MSA는 그저 "쪼개진 모놀리식"일 뿐이다. 이 책은 그 사실을 10장에 걸쳐 증명한다.

### Bounded Context — 어디에서 쪼개야 하는가

"쪼개야 한다"는 건 이해했다. 그런데 **어디에서** 쪼개야 하나? 마이크로서비스 설계의 가장 중요한 질문이다.

도메인 주도 설계(DDD)는 **Bounded Context(경계 컨텍스트)**라는 개념을 제시한다. 같은 단어가 서로 다른 뜻을 갖는 **언어의 경계**를 기준으로 삼으라는 것이다.

예를 들어 "상품"이라는 단어는:
- Product 서비스에서는 `{id, 이름, 가격, 재고}`를 뜻한다.
- Delivery 서비스에서는 `{id, 부피, 무게, 배송 난이도}`를 뜻한다.
- Review 서비스에서는 `{id, 평점, 리뷰 수, 평균}`을 뜻한다.

같은 "상품"이지만 관점이 다르다. 따라서 **각자 다른 서비스**에서 각자 다르게 다루는 것이 자연스럽다. 이것이 Bounded Context다.

chap044 프로젝트는 네 개의 경계 컨텍스트로 나뉘어 있다.
- **User 컨텍스트**: 로그인, 토큰 발급, 권한
- **Product 컨텍스트**: 상품 목록, 재고 관리
- **Order 컨텍스트**: 주문 기록, 상태 추적
- **Delivery 컨텍스트**: 배송지, 배송 상태

각 컨텍스트는 자기 일에만 집중하고, 다른 컨텍스트의 내부는 알지 못한다.

### Database per Service — 그런데 왜 이 프로젝트는 공유 DB인가

MSA의 교과서는 "서비스마다 자신만의 DB를 가져야 한다"고 가르친다. 이유는 명확하다.
- DB 스키마를 바꿀 때 다른 서비스에 영향을 주지 않기 위해
- 한 서비스의 DB 장애가 다른 서비스에 전파되지 않기 위해
- 한 서비스가 다른 서비스의 테이블에 **몰래** 접근하지 못하게 하기 위해

그런데 chap044는 **모든 서비스가 같은 MySQL 하나**를 공유한다. 왜인가? **교육용이기 때문이다.**

DB를 쪼개면 학습자가 다음을 동시에 배워야 한다.
- MSA 통신 (Kafka)
- 분산 트랜잭션 (Saga)
- 멱등성
- **서비스별 DB 관리 (각자 다른 MySQL 컨테이너)**

이 네 가지를 한꺼번에 다루면 학습자는 폭격당한다. 그래서 이 프로젝트는 DB를 공유한다. "원래는 쪼개야 한다는 것을 알면서, 교육의 편의를 위해 하나로 합쳤다"는 타협이다.

**13장(프로덕션으로 가려면)**에서 이 타협을 어떻게 풀 것인가를 다시 다룬다. 지금은 "공유 DB는 편의상의 선택이고, 진짜 MSA는 그렇지 않다"는 것만 기억하면 된다.

## 1.3 chap044에선 이렇게 한다

이 프로젝트에는 네 개의 **비즈니스 마이크로서비스**가 있다. (인프라 역할인 Gateway·Frontend·Orchestrator·Kafka·DB는 여기서 제외한다.)

| 서비스 | 포트 | 책임 | 주요 API/Kafka |
|---|---|---|---|
| **User** | 8083 | 로그인·JWT 발급 | `POST /api/login` |
| **Product** | 8082 | 상품·재고 관리 | `GET /api/products`, Kafka 명령 수신 |
| **Order** | 8081 | 주문 접수·상태 추적 | `POST /api/orders`, 웹소켓 푸시 |
| **Delivery** | 8084 | 배송 준비·완료 | `PUT /api/deliveries/{id}/complete` |

각 서비스는 **별도의 Spring Boot 애플리케이션**이고, 각자 `build.gradle`·`Dockerfile`·`application.properties`를 갖는다.

### 💻 코드 스냅샷 ① — 네 서비스의 엔트리 포인트

각 서비스는 모두 Spring Boot 애플리케이션이고, 엔트리는 놀랍도록 똑같다.

**`user/src/main/java/com/metacoding/user/UserApplication.java`**

```java
@SpringBootApplication
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

**`product/src/main/java/com/metacoding/product/ProductApplication.java`**, **`order/...OrderApplication.java`**, **`delivery/...DeliveryApplication.java`**도 패턴이 동일하다.

이것이 중요한 이유는 단 하나다. **각 서비스는 서로의 존재를 모른다.** 서로의 엔트리 클래스를 import할 수도 없고, 참조할 수도 없다. 그들이 아는 것은 **Kafka 토픽 이름**뿐이다. 이것이 마이크로서비스의 **느슨한 결합(loose coupling)**의 실체다.

### 책임의 분담 — 한 장면을 4등분하기

사용자가 MacBook Pro 한 대를 주문한다고 하자. 이 장면을 네 서비스가 어떻게 나누는지 보자.

- **User 서비스**: "ssar이 ssar@metacoding.com의 비밀번호 1234로 로그인했다. JWT 토큰을 준다."
- **Product 서비스**: "MacBook Pro 재고가 10개 있고, 1개를 차감해서 9개가 되었다."
- **Order 서비스**: "userId 1번이 productId 1번을 1개 주문했다. 주문 ID는 42번이고 상태는 PENDING이다."
- **Delivery 서비스**: "주문 42번에 대한 배송 기록을 만들었다. 배송지는 'Seoul'이고 상태는 READY다."

네 서비스는 자기 DB 테이블만 본다.
- User는 `user_tb`만 안다.
- Product는 `product_tb`만 안다.
- Order는 `order_tb`만 안다.
- Delivery는 `delivery_tb`만 안다.

그러나 "주문 42번을 완성시킨다"는 **비즈니스 프로세스**는 네 서비스를 모두 걸쳐서 일어난다. 이 프로세스를 조정하는 역할이 **오케스트레이터**다. 오케스트레이터의 본격적인 이야기는 6장에서 한다.

## 1.4 시뮬레이터로 확인하기

`simulator-msa-04.html` 파일을 열어서 두 번째 탭 **"🛒 MSA 비즈니스 흐름"**을 누른다.

화면 중앙에 네 개의 박스가 **2×2 그리드**로 보인다.
- 왼쪽 위: **User Service**
- 오른쪽 위: **Order Service**
- 왼쪽 아래: **Product Service**
- 오른쪽 아래: **Delivery Service**

네 박스 사이에는 **Kafka** 통로가 보이고, 위쪽에는 **Orchestrator(지휘자)**가 따로 자리 잡고 있다.

"▶ Happy Path" 버튼을 누르면, 네 서비스 사이로 **색깔 있는 작은 점**들이 이동하기 시작한다. 이 점들이 바로 **Kafka 메시지**다. 각 점이 어느 방향으로 가고 어느 박스에서 멈추는지를 관찰하자. 이 책 전체가 바로 **이 점들의 궤적을 설명하는 책**이다.

지금 단계에서는 "아하, 네 서비스가 따로따로 일하고 있구나. 그리고 서로 편지로만 말하는구나"만 느껴도 충분하다. 디테일은 이후 장에서 하나씩 풀 것이다.

## 1.5 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 한 점포로는 바쁜 시간대·장애 전파·전문성 문제를 해결할 수 없다.
- MSA는 애플리케이션을 **독립적인 서비스들의 집합**으로 쪼갠 구조다.
- Bounded Context를 기준으로 쪼개고, 서비스마다 **책임과 데이터를 모두 독립**시킨다.
- chap044는 교육 편의상 DB를 공유하지만, 실제 프로덕션은 Database per Service가 원칙이다.
- MSA는 공짜가 아니다. 트랜잭션·통신·디버깅이 모두 복잡해지고, 이를 해결하기 위한 도구로 **사가**와 **멱등성**이 등장한다.

다음 장은 네 점포가 숨 쉬는 도시의 지도를 그린다. 그 도시의 이름은 **쿠버네티스(Kubernetes)**다.

---

# 2장. 관제탑과 터널

## 2.1 비유로 보기 — 사거리의 관제탑과 대기하는 점장들

네 점포가 만들어졌다. 그런데 이 네 점포를 **도시 안에** 어떻게 배치할 것인가?

상상해보자. 도시의 한 사거리에 네 점포가 있다. 손님은 사거리 입구에 있는 **관제탑**에 먼저 도착한다. 관제탑은 손님의 요청을 보고 "아, 이건 상품 문의구나. 상품 점포로 안내하시오"라고 한다. 그리고 그 옆의 **안내 데스크**는 손님을 실제 상품 점포로 이끈다.

점포 자체는 단순한 건물이 아니다. 건물 안에는 **점장**이 있고, 점장은 여러 명의 직원(인력)을 갖는다. 손님이 많아지면 점장은 직원을 더 뽑는다. 직원 중 한 명이 병에 걸리면 점장은 그를 집에 보내고 **새 직원을 즉시 충원**한다. 이것이 점장의 핵심 역할이다. 즉 **점장은 "이 점포에 몇 명의 직원이 있어야 하는가"를 계속 감시하고 복구**한다.

모든 점포는 사거리 지도에 등록되어 있다. 손님은 "MacBook 점포 어디예요?"라고 물어보면 지도가 "저기 왼쪽 두 번째 건물이에요"라고 답해준다. 그래서 손님이 직접 건물 주소를 외울 필요가 없다.

그리고 도시 전체는 **문 하나**로만 외부와 통한다. 외부에서 도시에 들어오려면 반드시 그 문을 통과해야 하고, 문 앞에는 관제탑이 있다.

이 비유를 한 번에 정리하면:

| 비유 | 기술 용어 | 역할 |
|---|---|---|
| 점포 건물 | **Pod** | 실제 애플리케이션 인스턴스 |
| 점장 | **Deployment** | Pod의 복제본 수 관리·자가 치유 |
| 점포 지도 | **Service** | 여러 Pod을 하나의 주소로 묶어줌 |
| 도시의 문 | **Ingress** | 외부 접근의 유일한 진입점 |
| 관제탑 | **Gateway** (Nginx) | 요청의 종류를 보고 올바른 점포로 라우팅 |
| 도시 자체 | **Namespace** | 여러 점포가 모인 논리적 구역 |
| 도시의 비밀금고 | **Secret** | 비밀번호·키 같은 민감 정보 저장 |
| 도시의 공지판 | **ConfigMap** | DB 주소·환경설정 같은 공개 정보 저장 |

## 2.2 개념으로 정의하기 — 쿠버네티스의 기본 어휘

### Pod — 가장 작은 실행 단위

Pod은 쿠버네티스에서 관리하는 **최소 단위**다. 하나의 Pod은 하나 이상의 컨테이너를 포함하지만, 실무에서는 거의 대부분 **1 Pod = 1 컨테이너**라고 생각해도 된다.

Pod은 **일회용**이다. Pod은 언제든 죽을 수 있고, 새 Pod이 언제든 태어날 수 있다. Pod에는 고유한 IP가 있지만, 그 IP는 **Pod이 재시작되면 바뀐다**. 따라서 클라이언트가 Pod IP에 직접 접속하는 것은 어리석은 짓이다. 클라이언트는 반드시 **Service**라는 간접 계층을 통해야 한다.

### Deployment — 자가 치유의 주인공

Deployment는 "이 Pod을 **2개 유지하라**"고 선언하는 리소스다. 관리자가 "2개 유지"라고 선언하면, Deployment는 그것을 지키기 위해 밤낮없이 감시한다.
- Pod 하나가 죽으면 → 새 Pod을 즉시 만든다.
- Pod을 누가 강제로 삭제하면 → 그래도 새 Pod을 만든다.
- 노드(물리 서버) 하나가 죽으면 → 다른 노드에 새 Pod을 만든다.

이것이 쿠버네티스의 **자가 치유(self-healing)** 능력이다. 운영자가 밤새 서버가 살았는지 감시할 필요가 없다. Deployment가 대신 한다.

### Service — 주소록의 주인공

Pod은 IP가 바뀌는데, 어떻게 클라이언트가 안정적으로 접속할 수 있을까? 그것을 가능하게 하는 것이 **Service**다.

Service는 "이 레이블을 가진 Pod들을 모아서 **하나의 고정된 주소**로 만들어라"라고 선언한다. 예를 들어 `product-service`라는 Service는 `app=product` 레이블이 붙은 모든 Pod을 모아서, `product-service:8082`라는 고정 주소로 접근하게 해준다.

내부에서는 **kube-proxy**가 이 주소에 들어온 요청을 실제 Pod들에게 **로드 밸런싱**한다. 즉 Service는 단순한 주소록이 아니라 **로드 밸런서**이기도 하다.

### Ingress — 외부로 나가는 유일한 문

Service는 기본적으로 **클러스터 내부**의 주소다. 외부 인터넷에서 직접 `product-service:8082`에 접속할 수 없다.

외부에서 클러스터 안으로 들어오려면 **Ingress**가 필요하다. Ingress는 "이 도메인으로 들어오는 요청은 이 Service로 보내라"라는 규칙을 선언한다. chap044에서는 frontend와 gateway가 이 Ingress 뒤에 배치된다.

### ConfigMap과 Secret — 설정의 분리

"DB 주소가 `db-service:3306`이다"라는 정보는 **공개 정보**다. 이것은 **ConfigMap**에 담는다.

반대로 "DB 비밀번호가 `meta1234!`이다"라는 정보는 **민감 정보**다. 이것은 **Secret**에 담는다. Secret은 쿠버네티스 내부에서 base64로 인코딩되어 저장된다. (단, base64는 암호화가 아니라 **인코딩**이라는 점을 주의하자. 실제 프로덕션에서는 별도의 비밀 관리 도구가 필요하다.)

ConfigMap과 Secret은 Pod에 **환경변수** 또는 **파일**로 주입된다. 애플리케이션 코드는 이 값을 `@Value("${jwt.secret}")` 같은 방식으로 읽는다.

## 2.3 chap044에선 이렇게 한다

### K8s 구조 개요

chap044의 쿠버네티스 구조는 단순하다. 모든 리소스가 `metacoding`이라는 하나의 **네임스페이스**에 들어 있다.

```
Namespace: metacoding
│
├─ Deployment: user        (replicas: 2) → Pod × 2
├─ Service:    user-service
├─ ConfigMap:  user-configmap (DB URL 등)
├─ Secret:     user-secret    (DB 비밀번호, JWT 비밀키)
│
├─ Deployment: product     (replicas: 2)
├─ Service:    product-service
├─ ...
│
├─ Deployment: order       (replicas: 2)
├─ Deployment: delivery    (replicas: 2)
├─ Deployment: orchestrator (replicas: 1)  ← 단일 인스턴스
├─ Deployment: kafka        (replicas: 1)  ← 단일 브로커
├─ Deployment: db           (replicas: 1)  ← 단일 MySQL
├─ Deployment: gateway      (replicas: 1)  ← Nginx
├─ Deployment: frontend     (replicas: 1)  ← Nginx + HTML
│
└─ Ingress: frontend-ingress → frontend-service → gateway-service
```

주목할 점 세 가지.

첫째, **비즈니스 서비스(User, Product, Order, Delivery)는 replicas 2**이다. 즉 각 서비스당 Pod이 2개씩 떠 있다. 이로써 한 Pod이 죽어도 나머지 한 Pod이 요청을 받을 수 있다.

둘째, **Orchestrator·Kafka·DB는 replicas 1**이다. 이것은 **교육용이라 단순화한 것**이다. 실제 프로덕션에서는:
- Kafka는 3개 브로커 이상 (ISR 과반수 합의를 위해)
- DB는 Primary + Replica 또는 Galera 클러스터
- Orchestrator는 상태 저장소(Redis 등)를 공유하는 복수 인스턴스

셋째, **Service는 대부분 ClusterIP**다. 즉 클러스터 내부에서만 접근 가능하다. 외부로 노출되는 것은 오직 **Ingress를 통한 frontend**뿐이다. 이로써 공격면(attack surface)이 최소화된다.

### 게이트웨이 라우팅

Nginx Gateway는 들어온 HTTP 경로를 보고 어느 Service로 보낼지 결정한다.

```
/api/login      → user-service:8083
/api/products   → product-service:8082
/api/orders     → order-service:8081
/api/deliveries → delivery-service:8084
/ws             → order-service:8081  (웹소켓)
/               → frontend-service:80 (정적 페이지)
```

이것을 "관제탑이 손님의 용건을 듣고 올바른 점포로 안내한다"는 비유로 이해하면 된다. 실제 Nginx 설정은 `gateway/nginx.conf`에 있다.

### 💻 코드 스냅샷 — Gateway 라우팅 (참고)

책의 핵심 코드 스냅샷은 아끼기로 했다. 그러나 Nginx 설정은 **코드라기보다 설정**이므로, 독자의 이해를 돕기 위해 일부만 인용한다.

**`gateway/nginx.conf`** (발췌)

```nginx
server {
    listen 80;

    location /api/orders {
        proxy_pass http://order-service;
    }

    location /ws {
        proxy_pass http://order-service;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

`proxy_pass`는 "이 경로로 온 요청을 저 내부 Service로 전달하라"는 의미다. `/ws`는 웹소켓이라 `Upgrade` 헤더를 유지해야 하므로 추가 설정이 필요하다. (웹소켓의 본격적인 이야기는 10장에서 한다.)

### Minikube 터널 — 왜 필요한가

개발자가 로컬에서 쿠버네티스를 연습할 때 가장 자주 쓰는 도구가 **Minikube**다. Minikube는 쿠버네티스 클러스터를 로컬 노트북 안에서 흉내 내주는 도구다.

그런데 Minikube를 **Docker driver**로 실행하면, 쿠버네티스 클러스터 전체가 **하나의 거대한 Docker 컨테이너** 안에 갇힌다. 이 컨테이너는 개발자 노트북의 네트워크와 격리되어 있다. 그래서 개발자가 브라우저로 `http://metacoding.local`을 쳐도 그 요청은 Minikube 내부에 닿지 않는다.

이 격리를 뚫어주는 것이 `minikube tunnel` 명령이다.

```
┌─────────────────────────────────────────────────┐
│ 개발자 노트북                                    │
│                                                  │
│  브라우저                                        │
│     │ http://metacoding.local                    │
│     ▼                                            │
│  /etc/hosts (127.0.0.1 metacoding.local)         │
│     │                                            │
│     ▼                                            │
│  minikube tunnel ──(포트포워딩)──┐               │
│                                   │               │
│  ┌────────────────────────────────┼────────────┐ │
│  │ Minikube Docker 컨테이너        │            │ │
│  │                                 ▼            │ │
│  │   Ingress Controller                        │ │
│  │     │                                       │ │
│  │     ▼                                       │ │
│  │   frontend-service → gateway-service → ... │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

즉 두 가지가 함께 필요하다.
1. `/etc/hosts`에 `127.0.0.1 metacoding.local` 등록 (DNS를 로컬로 돌림)
2. `minikube tunnel` 실행 (컨테이너 벽을 뚫음)

이것은 "프로덕션 쿠버네티스에는 필요 없고 **로컬 개발 환경에서만** 필요한 편법"이다. 실제 프로덕션은 클라우드 로드밸런서나 실제 DNS를 사용한다.

## 2.4 시뮬레이터로 확인하기

시뮬레이터의 **첫 번째 탭 "🏗 쿠버네티스 세상"**을 누른다. 도시의 지도가 펼쳐진다.

화면에서 확인할 수 있는 것들:
- 맨 왼쪽의 **Browser**
- 브라우저가 도시로 들어가는 문인 **Ingress Controller Pod**
- 도시의 경계인 **K8s Node** (큰 회색 박스)
- 그 안에 여섯 개의 Service가 있고, 각 Service마다 **Pod 두 개**가 나란히 서 있다.

"💀 Pod1 강제 종료" 버튼을 눌러보자. 해당 Pod이 빨간 선으로 사라진다. 몇 초 후, **자동으로 새 Pod이 태어난다**. 이것이 Deployment의 **자가 치유**다.

"▶▶ 10번 반복" 버튼을 눌러보자. 10개의 요청이 Service에 닿으면, **두 개의 Pod에 번갈아** 요청이 간다. 이것이 **로드 밸런싱**이다.

시뮬레이터의 **세 번째 탭 "🚇 Ingress 터널"**로 가면, Minikube 터널의 필요성을 시각적으로 확인할 수 있다.

## 2.5 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- Pod은 최소 실행 단위이고, IP가 바뀌며, 일회용이다.
- Deployment는 Pod의 개수를 유지하고, 자가 치유를 담당한다.
- Service는 여러 Pod을 고정 주소와 로드 밸런서로 묶어준다.
- Ingress는 외부에서 들어오는 유일한 문이다.
- ConfigMap은 공개 설정, Secret은 민감 설정을 담는다.
- chap044는 네임스페이스 `metacoding` 안에 8개 Deployment를 올린다.
- Minikube tunnel은 Docker driver 때문에 필요한 로컬 개발용 편법이다.

이 장은 **쿠버네티스의 간략 지도**만 그렸다. 이 책의 중심은 쿠버네티스가 아니다. 이 책의 중심은 **서비스들이 서로 어떻게 대화하는가**이다. 그리고 그 대화의 첫 단추는 **누가 누구인지**를 증명하는 인증이다. 다음 장의 주제다.

---

# 3장. 손도장 하나로 통한다

## 3.1 비유로 보기 — 놀이공원의 손도장

놀이공원 입구에서 입장료를 낸 손님은 손등에 **형광 손도장**을 찍는다. 그 손도장만 있으면 놀이공원 안의 어떤 놀이기구든 재확인 없이 탈 수 있다. 입구를 빠져나갔다가 다시 들어올 때도 손도장만 보여주면 된다. 손도장은 몇 시간 후 저절로 희미해진다. 희미해지면 다시 입장료를 내야 한다.

이 손도장의 특징을 정리해보자.
- 입구(인증 기관)만 손도장을 찍어줄 수 있다.
- 놀이공원 안의 직원 누구나 손도장을 **확인**할 수 있다 (특수 자외선 램프만 있으면).
- 손도장 자체에는 **손님의 이름과 입장 시간**이 적혀 있다.
- 손도장은 **위조할 수 없다**. 형광 잉크와 스탬프가 놀이공원만의 것이기 때문이다.
- 손도장은 **유효 기간이 있다**. 무한정 유효하면 보안이 무너진다.

이 다섯 가지 특성이 바로 **JWT(JSON Web Token)**의 다섯 가지 성질이다.

## 3.2 개념으로 정의하기 — JWT란 무엇인가

### 세션 방식 vs 토큰 방식

전통적인 웹은 **세션 기반 인증**을 썼다. 사용자가 로그인하면 서버는:
1. 랜덤한 세션 ID를 생성한다.
2. 그 세션 ID를 서버 메모리(또는 DB)에 저장한다. 값에는 사용자 정보가 붙어 있다.
3. 그 세션 ID를 쿠키로 클라이언트에 내려준다.
4. 이후 요청마다 클라이언트가 세션 ID를 함께 보낸다.
5. 서버는 세션 ID를 DB에서 찾아 사용자를 식별한다.

이 방식의 문제는 **서버가 상태(state)를 갖는다**는 점이다. 서버 인스턴스가 10개라면 그 10개 서버가 모두 같은 세션 DB를 공유해야 한다. 그렇지 않으면 한 서버에서 로그인한 사용자가 다른 서버에는 "너 누구야?"를 당한다.

**토큰 기반 인증**은 이 상태를 아예 없앤다. 사용자가 로그인하면 서버는:
1. **사용자 정보 자체를 담은 토큰**을 만든다.
2. 토큰에 **서버의 비밀키로 서명**한다.
3. 토큰을 클라이언트에 내려준다.
4. 이후 요청마다 클라이언트가 토큰을 함께 보낸다.
5. 서버는 토큰의 **서명만 검증**하면 된다. DB 조회도 세션 저장도 필요 없다.

이것이 **무상태(stateless)** 인증이다. 서버가 몇 대이든 상관없다. 모든 서버가 같은 비밀키만 알면 된다.

### JWT의 3부 구조

JWT는 세 부분이 마침표(`.`)로 연결된 문자열이다.

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJzc2FyIiwiZXhwIjoxNzA1Mjg3NjAwfQ.AbCdEf...
│                    │                                                              │
│                    │                                                              │
│ Header             │ Payload                                                      │ Signature
│ (알고리즘 정보)     │ (실제 데이터)                                                │ (서명)
```

**Header**: 어떤 알고리즘으로 서명했는지. `{"alg":"HS512"}` 같은 JSON을 base64로 인코딩한 것.

**Payload**: 실제 담긴 데이터. `{"sub":"1","username":"ssar","exp":1705287600}` 같은 JSON을 base64로 인코딩한 것. 여기서:
- `sub`: subject, 이 토큰의 주인 (주로 userId)
- `exp`: expiration, 만료 시각 (UNIX epoch seconds)
- `username`: 커스텀 클레임 (서비스가 원하는 아무 필드나 추가 가능)

**Signature**: Header와 Payload를 비밀키로 서명한 결과. 서명 덕분에 누군가가 Payload를 몰래 바꿔도 즉시 탄로난다.

**중요한 주의사항**: Payload는 **base64 인코딩**일 뿐 **암호화**가 아니다. 누구나 Payload를 디코딩해서 내용을 볼 수 있다. 따라서 **비밀번호 같은 민감 정보를 JWT에 담으면 안 된다**. JWT에 담는 것은 "식별자와 권한" 정도여야 한다.

### 서명과 검증

JWT의 서명은 **HMAC** 또는 **RSA** 같은 알고리즘으로 만들어진다. chap044는 **HMAC-SHA512**를 쓴다.

HMAC 서명의 원리는 간단하다.
```
signature = HMAC-SHA512(비밀키, Header + "." + Payload)
```

검증 쪽은 이렇게 한다.
```
1. 받은 토큰을 Header, Payload, Signature로 나눈다.
2. 서버의 비밀키로 Header + Payload를 다시 HMAC-SHA512 한다.
3. 그 결과가 받은 Signature와 일치하면 → 위조되지 않았다.
4. 일치하지 않으면 → 누군가 Payload를 손댔다. 거부한다.
```

HMAC의 핵심은 **비밀키를 모르면 같은 서명을 만들 수 없다**는 것이다. 그래서 비밀키는 서버 밖으로 절대 나가면 안 된다.

### 만료 시간

만료 시간이 없는 토큰은 **영구적인 열쇠**와 같다. 토큰이 한 번이라도 유출되면 영원히 도용된다.

그래서 JWT는 반드시 `exp`를 가져야 한다. 보통 **짧은 수명(15분~24시간)**을 준다. 수명이 짧을수록 도용의 위험이 줄지만, 대신 사용자가 자주 다시 로그인해야 한다. 이 균형을 조정하는 것이 **리프레시 토큰** 패턴인데, 이 책의 범위는 넘어선다.

chap044는 `jwt.expiration=86400000`ms, 즉 **24시간**으로 설정되어 있다.

## 3.3 chap044에선 이렇게 한다

### 전체 흐름

1. 사용자가 `/api/login`에 `username=ssar, password=1234`를 POST 한다.
2. User 서비스는 `user_tb`를 조회해서 일치하는 행을 찾는다.
3. 찾으면 `JwtProvider.create(userId, username)`을 호출해서 JWT를 만든다.
4. JWT를 JSON 응답으로 돌려준다: `{"token":"eyJhb...","userId":1,"username":"ssar"}`.
5. 클라이언트(브라우저)는 이 토큰을 `localStorage`에 저장한다.
6. 이후 주문을 낼 때, 클라이언트는 `Authorization: Bearer <토큰>` 헤더를 붙여서 `/api/orders`에 POST 한다.
7. Order 서비스의 필터는 `Authorization` 헤더의 토큰을 검증한다. 유효하면 `userId`를 꺼내서 주문에 기록한다. 유효하지 않으면 **401 Unauthorized**를 돌려준다.

### 💻 코드 스냅샷 ② — JWT 발급 핵심

`user/src/main/java/com/metacoding/user/util/JwtProvider.java` (발췌)

```java
public String create(int userId, String username) {
    return JWT.create()
            .withSubject(String.valueOf(userId))
            .withClaim("username", username)
            .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
            .sign(Algorithm.HMAC512(secret));
}
```

네 줄짜리 메서드인데 JWT의 모든 핵심이 들어 있다.
- `withSubject(userId)` — Payload의 `sub`에 사용자 ID를 넣는다.
- `withClaim("username", username)` — 커스텀 클레임으로 사용자명을 넣는다.
- `withExpiresAt(...)` — 만료 시각을 현재 + 24시간으로 설정한다.
- `sign(Algorithm.HMAC512(secret))` — `application.properties`의 `jwt.secret` 값으로 서명한다.

### 검증 쪽

Order 서비스의 `JwtVerifier`는 반대 방향으로 일한다. 요청이 들어올 때마다:
1. `Authorization` 헤더에서 `Bearer ` 접두사를 떼고 토큰만 추출한다.
2. 같은 `jwt.secret`으로 서명을 검증한다.
3. 검증 성공 시 `sub`에서 `userId`를 꺼낸다.
4. 실패 시 null을 리턴해서 컨트롤러가 401을 반환하게 한다.

여기서 중요한 아키텍처 결정이 숨어 있다. **User 서비스와 Order 서비스는 같은 비밀키를 공유한다**. 그래서 User가 발급한 토큰을 Order가 검증할 수 있다. 이 비밀키는 K8s Secret에 담겨 두 서비스의 환경변수로 주입된다.

### JWT 디코딩 — 프론트엔드의 트릭

프론트엔드도 JWT 안의 `userId`가 필요할 때가 있다. 웹소켓 구독 경로가 `/topic/orders/{userId}`이기 때문이다. 그런데 프론트엔드는 서버의 비밀키를 모른다. 어떻게 userId를 알아낼 수 있을까?

답: **서명 검증은 하지 않고 Payload만 디코딩**한다.

`frontend/index.html` (발췌)

```javascript
function decodeJwt(tk) {
    const payload = tk.split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/');
    const pad = payload.length % 4 === 0
        ? '' : '='.repeat(4 - payload.length % 4);
    return JSON.parse(atob(payload + pad));
}
```

이것은 "**Payload는 누구나 읽을 수 있다**"는 JWT의 성질을 활용한 것이다. 프론트엔드는 이 방식으로 userId를 읽어서 웹소켓 구독에만 쓴다. 실제 보안 결정(주문을 허용할지 말지)은 서버의 서명 검증이 담당한다.

## 3.4 시뮬레이터로 확인하기

첫 번째 탭 "🏗 쿠버네티스 세상"에서 **"▶ 로그인 요청 1번"** 버튼을 눌러보자.

보라색 점이 브라우저에서 Ingress를 거쳐 Gateway로, 그리고 user-service의 Pod까지 이동한다. 응답이 되돌아올 때, 브라우저에는 **🔓 상태가 🔐 상태**로 바뀐다. 손도장을 받은 것이다.

그 다음 "▶ 주문 전체 흐름" 버튼을 누르면, 이후의 요청마다 보라색 JWT 배지가 따라다닌다. 어느 서비스든 이 배지만 보면 "아, 이 요청은 ssar이 낸 것이구나"를 알 수 있다. 별도의 DB 조회 없이 말이다.

## 3.5 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 세션 기반은 서버에 상태를 남기고, 토큰 기반은 상태가 없다.
- JWT는 Header·Payload·Signature 세 부분으로 구성된다.
- Payload는 누구나 읽을 수 있으므로 **민감 정보를 넣지 않는다**.
- Signature는 HMAC 서명으로 만들어지며, 비밀키가 없으면 위조 불가능하다.
- 만료 시간(exp)이 없으면 영구 열쇠가 되므로 반드시 짧게 설정한다.
- chap044는 User 서비스가 발급하고 Order 서비스가 검증하며, 같은 비밀키를 K8s Secret으로 공유한다.

손도장을 받은 사용자가 이제 점포들 사이로 편지를 보낸다. 그런데 편지는 누가 배달하나? 점포끼리 직접 발품 파는 것이 아니라 **방송실**이 한다. 다음 장에서 그 방송실의 원리를 뜯어본다.

---

# 4장. 전령 대신 방송실을 두기로

## 4.1 비유로 보기 — 전령의 한계와 방송실의 등장

네 점포가 의사소통해야 한다. 처음에는 **전령(REST 호출)**을 쓴다. Order 점포가 할 일이 생기면 Product 점포로 직접 달려가 "MacBook Pro 하나 차감해줘"라고 말한다. Product 점포가 "네, 차감했어요"라고 답하면 Order 점포는 뒤돌아서 Delivery 점포로 달려간다. "이 주문의 배송을 준비해줘."

이 방식은 두 가지 문제가 있다.

**첫째, 전령은 답을 기다리며 서 있어야 한다.** Product 점포에서 3초를 기다리고, Delivery 점포에서 또 3초를 기다리면, Order 점포는 총 6초를 서 있다. 그동안 Order 점포는 다른 손님을 받을 수 없다. **동기(synchronous) 호출**의 숙명이다.

**둘째, 점포 하나가 문을 닫으면 전령은 실패한다.** Product 점포가 점심 시간이라고 문을 닫으면, 전령은 답을 받지 못한다. 결과적으로 Order 점포도 대응을 못 한다. **장애가 전파**되는 것이다.

그래서 점포들은 마을 한가운데 **방송실**을 세우기로 한다. 규칙은 이렇다.
- 어떤 점포가 할 말이 있으면 **방송실에 편지를 붙인다**.
- 다른 점포들은 **관심 있는 편지지만 구독**한다.
- 편지가 붙으면 구독자들에게 **자동으로 전달**된다.
- 편지를 보낸 점포는 **누가 읽었는지, 언제 답할지 관심 없다**.

이 방송실의 이름이 **Kafka**다.

### 방송실이 주는 네 가지 선물

**선물 1: 점포는 답을 기다리지 않는다.** Order 점포는 편지만 붙이고 즉시 다음 손님을 받는다. 응답 시간이 평균 6초에서 0.1초로 줄어든다.

**선물 2: 점포가 문을 닫아도 편지는 남는다.** Product 점포가 점심으로 문을 닫으면, 편지는 방송실의 게시판에 그대로 붙어 있다. Product 점포가 점심을 먹고 돌아오면, 그때 편지를 읽고 처리한다. **편지가 소실되지 않는다**.

**선물 3: 편지 하나를 여러 점포가 본다.** "주문이 생성됨"이라는 편지를 Product 점포, Delivery 점포, 통계 점포가 모두 구독할 수 있다. 한 사건에 여러 반응을 동시에 일으킬 수 있다.

**선물 4: 점포를 늘리면 편지도 나눠 처리한다.** Product 점포를 두 개로 늘리면, 편지 10장이 오면 각각 5장씩 나눠 처리한다. **수평 확장**이 자연스럽게 일어난다.

## 4.2 개념으로 정의하기 — 카프카의 기본 어휘

### Kafka의 역할

Kafka는 **분산 메시지 브로커**다. 메시지를 만드는 쪽(Producer)과 읽는 쪽(Consumer) 사이에 서서, 메시지를 **지속적으로 저장**하고 **필요할 때 전달**한다.

기존의 메시지 브로커(RabbitMQ 같은 AMQP 계열)와 Kafka의 가장 큰 차이는 **메시지를 읽어도 사라지지 않는다**는 점이다. Kafka는 메시지를 **로그 파일**처럼 쌓아두고, 컨슈머가 "나 지금 3번째 메시지까지 읽었어"라는 **오프셋(offset)**을 관리한다. 같은 메시지를 여러 번 읽을 수도 있고, 여러 컨슈머가 같은 메시지를 독립적으로 소비할 수도 있다.

### Broker, Topic, Partition, Offset

**Broker**: Kafka 서버 인스턴스 자체. 실제로 메시지를 저장하고 중계한다. chap044는 Broker 하나만 돌리지만, 프로덕션에서는 3개 이상으로 구성한다. 여러 Broker가 모인 것을 **Kafka Cluster**라 부른다.

**Topic**: 메시지의 카테고리. "주문 생성 소식지"는 `order-created-event`라는 Topic이고, "재고 차감 명령장"은 `product-decrease-command`라는 Topic이다. Topic은 이름이 전부이고, 별다른 스키마 강제는 없다(단, 스키마 레지스트리를 쓰면 강제 가능).

**Partition**: Topic은 내부적으로 하나 이상의 **Partition**으로 쪼개진다. 파티션은 Topic의 물리적 단위다. 메시지는 Topic이 아니라 **Partition에 순서대로 append**된다.
- 파티션이 3개면 Topic의 처리량을 3배로 늘릴 수 있다.
- 같은 키(key)를 가진 메시지는 항상 같은 파티션으로 간다 → 키 단위 순서 보장.
- 다른 파티션 간에는 순서가 보장되지 않는다.

chap044는 토픽별 파티션 수를 기본값(대개 1)으로 쓴다. 실전에서는 예상 처리량과 컨슈머 수에 맞게 조정한다.

**Offset**: 파티션 안의 메시지 순번. 0번, 1번, 2번… 순서대로 번호가 붙는다. 컨슈머는 "나는 오프셋 42까지 처리했어"라는 정보(커밋 오프셋)를 Kafka에 저장한다. 이 정보 덕에 컨슈머가 재시작해도 처리하던 지점에서 다시 시작할 수 있다.

```
Topic: product-decrease-command
│
├─ Partition 0: [msg0, msg1, msg2, msg3, msg4, ...]
│              ↑
│              consumer-group=product-service의 오프셋=4
│
├─ Partition 1: [msg0, msg1, msg2, ...]
│
└─ Partition 2: [msg0, msg1, ...]
```

### Producer와 Consumer

**Producer**: 메시지를 **발행(publish)**하는 쪽. Spring에서는 `KafkaTemplate.send(topic, key, value)`를 쓴다.

**Consumer**: 메시지를 **구독(subscribe)**해서 읽는 쪽. Spring에서는 `@KafkaListener(topics="...", groupId="...")`를 쓴다.

Producer와 Consumer는 서로를 모른다. Producer는 토픽 이름만 알면 발행할 수 있고, Consumer는 토픽 이름만 알면 구독할 수 있다. 이것이 **디커플링(decoupling)**의 핵심이다.

### Consumer Group — 수평 확장의 열쇠

Consumer들은 **Consumer Group**에 속한다. 같은 Group ID를 가진 컨슈머들은 **하나의 논리적 컨슈머**로 취급된다.

예를 들어 `product-service`라는 Group ID를 가진 컨슈머가 3개 있다고 하자. Topic의 파티션이 3개라면, Kafka는 각 컨슈머에게 파티션을 하나씩 할당한다. 그 결과:
- Partition 0 → Consumer A
- Partition 1 → Consumer B
- Partition 2 → Consumer C

세 컨슈머는 **서로 다른 메시지**를 병렬 처리한다. 같은 메시지를 중복 처리하지 않는다. 이것이 수평 확장의 핵심이다.

반면에 **다른 Group**이면 이야기가 다르다. `product-service` 그룹과 `stats-service` 그룹이 같은 Topic을 구독하면, **각 그룹은 모든 메시지를 독립적으로** 받는다. 같은 주문 메시지를 Product가 처리하면서 동시에 Stats도 처리할 수 있다.

```
Topic: order-created-event
(파티션 1개, 메시지 10장)
│
├─► Consumer Group: product-service  → 10장 모두 읽음 (내부 3개 컨슈머가 나눔)
│
└─► Consumer Group: stats-service    → 10장 모두 독립적으로 읽음
```

chap044의 그룹 구성은 이렇다.

| Group ID | 어느 서비스 | 어떤 토픽 구독 |
|---|---|---|
| `orchestrator` | Orchestrator | `order-created-event`, `product-decreased-event`, `delivery-created-event`, `delivery-completed-event` |
| `product-service` | Product | `product-decrease-command`, `product-increase-command` |
| `order-service` | Order | `order-complete-command`, `order-cancel-command` |
| `delivery-service` | Delivery | `delivery-create-command` |

### Push vs Pull

**Push 방식**: 브로커가 컨슈머에게 메시지를 밀어 넣는다. 컨슈머가 느리면 브로커의 버퍼가 차오른다.

**Pull 방식**: 컨슈머가 브로커에 메시지를 달라고 요청한다(polling). 컨슈머가 자신의 속도로 조절할 수 있다.

Kafka는 **Pull 방식**이다. 이 선택 덕에 컨슈머는 자기가 감당할 수 있는 만큼만 메시지를 가져올 수 있다. 빠른 컨슈머는 빠르게, 느린 컨슈머는 느리게. 이 자체가 **일종의 자연스러운 백프레셔(backpressure)**가 된다.

### KRaft 모드 — Zookeeper가 사라진 이유

과거의 Kafka는 **Zookeeper**라는 별도의 분산 조정 시스템에 메타데이터(어떤 토픽이 있고, 어떤 파티션이 어느 브로커에 있는지)를 저장했다. 그래서 Kafka를 운영하려면 Zookeeper도 같이 운영해야 했고, 두 시스템의 버전 호환성도 관리해야 했다.

Kafka 2.8부터는 **KRaft(Kafka Raft) 모드**가 도입되었다. 이제 Kafka는 **자체적으로 Raft 합의 알고리즘**을 써서 메타데이터를 관리한다. Zookeeper가 필요 없다.

chap044의 Kafka는 KRaft 모드 **단일 노드**다. 즉 **브로커와 컨트롤러를 같은 프로세스에서** 돌린다. K8s 매니페스트의 환경변수에서 `KAFKA_PROCESS_ROLES=broker,controller`로 설정되어 있다.

```
Kafka Pod (단일)
│
├─ Role 1: broker    (메시지 저장·전달)
├─ Role 2: controller (메타데이터 관리)
│
├─ 9092 포트: 클라이언트용
└─ 9093 포트: 컨트롤러용
```

실제 프로덕션은 Broker와 Controller를 분리하고, 각각 3대 이상 쿼럼(quorum)을 구성한다.

### 전달 보장 — At-most-once, At-least-once, Exactly-once

Kafka는 기본적으로 **At-least-once(최소 한 번)** 전달을 보장한다. 이 말은:
- 메시지는 **절대 잃어버리지 않는다** (성공적으로 발행되면).
- 그러나 **중복 전달될 수 있다**.

왜 중복이 생기는가? Producer가 메시지를 브로커에 보냈는데, 브로커의 ACK가 네트워크 문제로 Producer에 도착하지 않는 경우를 상상해보자. Producer는 "혹시 못 받았나?" 하고 재시도한다. 결과: 브로커에는 같은 메시지가 두 번 저장된다.

또한 Consumer 쪽에서도 중복이 생긴다. Consumer가 메시지를 처리하고 아직 오프셋을 커밋하지 못한 상태에서 재시작되면, 그 메시지를 다시 받게 된다.

이 중복을 피하는 세 가지 전달 보장:

| 보장 | 의미 | 특징 |
|---|---|---|
| **At-most-once** | 잃어버릴 수 있지만 중복은 없다 | 재시도 없음. 가장 빠름. 로그 같은 비중요 데이터용 |
| **At-least-once** | 중복될 수 있지만 잃어버리지 않는다 | 재시도 있음. Kafka의 기본. **멱등성으로 중복 제거** |
| **Exactly-once** | 정확히 한 번 | Transactional Producer + Read Process Write 전 구간 트랜잭션 필요. 비쌈 |

chap044는 **At-least-once를 받아들이고, 멱등성으로 중복을 흡수**하는 전략을 쓴다. 이것이 이 책의 8장 "같은 전갈을 두 번 받으면"의 핵심 주제다. **지금 이 대목을 이해하는 것이 중요하다.** At-least-once의 중복은 Kafka의 본질이고, 이를 전제로 모든 시스템이 설계되어야 한다.

### 동기 REST vs 비동기 Kafka — 연쇄 장애의 이야기

Kafka가 동기 REST보다 나은 이유를 구체적으로 보자.

**시나리오: 주문 → 재고 차감 → 배송 생성**

**동기 REST 방식**
```
[Order] ──HTTP──► [Product]
                      │
                      └──HTTP──► [Delivery]
                                     │
[Order] ◄── 완료 응답 (3초 소요) ◄───┘
```
- Order는 3초 동안 블록된다.
- Delivery가 죽으면 Product도 대기하고, 결국 Order도 실패한다. **장애가 역방향으로 전파**.
- 한 요청에 3개 서비스가 모두 살아 있어야 성공한다. 가용성이 **각 서비스 가용성의 곱**으로 떨어진다. (99% × 99% × 99% = 97%)

**비동기 Kafka 방식**
```
[Order] ──publish──► [Kafka: order-created-event]
                         │
[Orchestrator] ◄─subscribe
                         │
                    publish
                         │
       ┌─────────────────┴────────────────┐
       ▼                                   ▼
 [Kafka: product-decrease-command]   (다른 토픽들)
       │
       ▼
 [Product]
```
- Order는 publish하자마자 0.1초 안에 응답한다.
- Delivery가 죽어도 Order·Product는 정상 동작한다.
- Delivery가 살아나면 쌓였던 메시지를 **순서대로 처리**한다. **비동기 복원력**.

이 장점의 대가는 **즉시성의 포기**다. 사용자는 "주문 접수"라는 즉시 응답을 받지만, "주문 완료"라는 최종 상태를 받으려면 후속 처리가 끝나기를 기다려야 한다. 그래서 **웹소켓**이나 **폴링**이 짝을 이룬다. 이 이야기는 10장에서 한다.

## 4.3 chap044에선 이렇게 한다

### Kafka 배치

chap044의 Kafka는 K8s 클러스터 안에 **단일 Broker + 단일 Controller** 구성의 Pod으로 떠 있다. 내부 주소는 `kafka-service:9092`다.

각 서비스의 `application.properties`에는 이렇게 적혀 있다.

```properties
spring.kafka.bootstrap-servers=kafka-service:9092
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.group-id=product-service
```

- `bootstrap-servers`: Kafka 클러스터의 진입 주소. 여기만 알면 나머지 브로커 정보를 자동으로 받는다.
- `auto-offset-reset=earliest`: 컨슈머가 처음 접속할 때 **가장 오래된 메시지부터** 읽는다. `latest`는 "새 메시지만" 읽는 설정이다.
- `value-deserializer=JsonDeserializer`: 메시지의 Value를 JSON으로 역직렬화.
- `trusted.packages=*`: 보안상 원래는 패키지를 제한하지만 교육용이라 모두 허용.

### 토픽은 누가 만드나

Kafka의 토픽은 **관리자가 미리 만들 수도** 있고 **자동으로 생성되게 할 수도** 있다. chap044는 `auto.create.topics.enable=true`(Kafka 기본값)에 의존해서, 첫 번째 Producer가 publish하는 순간 토픽이 생성되도록 한다. 이것도 **교육 편의**다. 프로덕션에서는 명시적으로 토픽을 만들고 파티션 수와 복제 계수를 관리한다.

### 💻 코드 스냅샷 ③ — Producer와 Consumer 한 세트

**Producer (Order 서비스)**

```java
// Order가 주문을 저장한 뒤 Kafka로 알림
kafkaTemplate.send("order-created-event",
        String.valueOf(order.getId()),  // key
        new OrderCreatedEvent(order.getId(), order.getUserId(),
                order.getProductId(), order.getQuantity(),
                delivery.getAddress()));
```

- 첫 번째 인자: 토픽 이름
- 두 번째 인자: **키(key)**. 여기서는 orderId. 같은 키의 메시지는 같은 파티션으로 가서 순서가 보장된다.
- 세 번째 인자: 메시지 본문(Value). Spring Kafka가 JSON으로 직렬화해서 보낸다.

**Consumer (Orchestrator)**

```java
@KafkaListener(topics = "order-created-event", groupId = "orchestrator")
public void onOrderCreated(OrderCreatedEvent event) {
    int orderId = event.getOrderId();
    // 이후 처리...
}
```

- `@KafkaListener`는 "이 토픽을 이 Group ID로 구독하라"는 선언이다.
- Spring이 알아서 메시지를 역직렬화해서 `OrderCreatedEvent` 객체로 만들어준다.
- 이 메서드가 예외 없이 끝나면 Spring Kafka가 **자동으로 오프셋을 커밋**한다. 예외가 터지면 커밋하지 않아서 재시도된다.

이 두 조각이 Kafka 통신의 **모든 코드**다. 나머지는 모두 설정과 DTO뿐이다.

### 메시지의 "key" — 왜 orderId로 키를 두나

`kafkaTemplate.send("...", String.valueOf(orderId), payload)`에서 두 번째 인자가 **key**다.

Kafka는 `hash(key) % partitionCount`로 파티션을 결정한다. 따라서:
- 키가 같으면 → 같은 파티션 → 순서 보장
- 키가 다르면 → 다른 파티션일 수 있음 → 순서 미보장

chap044에서 같은 `orderId`에 관련된 메시지들(`decrease`, `create-delivery`, `complete`)은 **모두 같은 파티션**에 가야 순서 보장이 된다. 그래서 키로 `orderId`를 쓴다.

주문 A와 주문 B는 서로 다른 파티션에 갈 수도 있지만, 서로 독립적인 거래이므로 상관없다. 같은 주문 안에서의 순서만 지켜지면 된다. **이것이 Kafka에서 파티션 키를 선택하는 원칙**이다.

## 4.4 시뮬레이터로 확인하기

두 번째 탭 "🛒 MSA 비즈니스 흐름"을 보자. 중앙에 **Kafka**라는 큰 박스가 있다. 그 안에 여러 **토픽 슬롯**이 있고, 각 슬롯 앞에는 노란 점이 쌓일 수 있다.

"▶ Happy Path"를 누르면:
1. Order가 `order-created-event` 슬롯에 노란 점을 올린다.
2. Orchestrator가 그 점을 소비한다.
3. Orchestrator가 `product-decrease-command` 슬롯에 노란 점을 올린다.
4. Product가 소비한다.
5. … (이하 반복)

"⏸ 일시정지" 후 "▶ 단계별 재개" 버튼으로 **한 걸음씩** 진행할 수 있다. 각 걸음마다 어느 토픽에 어떤 점이 올라가고, 어느 컨슈머가 어떤 점을 집어가는지 관찰하자.

중요한 관찰 포인트는 **토픽이 9개 있다는 것**이다. 왜 9개나 필요한지, 그리고 각 토픽이 명령인지 이벤트인지는 다음 장(5장)의 주제다.

## 4.5 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 동기 REST는 응답 대기와 연쇄 장애의 문제를 갖는다.
- Kafka는 비동기 메시지 브로커로, 메시지를 로그처럼 쌓아두고 컨슈머가 오프셋으로 읽는다.
- Broker(서버), Topic(카테고리), Partition(물리 단위), Offset(순번)이 기본 어휘다.
- Producer와 Consumer는 서로를 모르고 토픽 이름만 공유한다.
- Consumer Group은 여러 컨슈머를 하나의 논리 컨슈머로 묶어 수평 확장을 가능하게 한다.
- Kafka는 Pull 방식이고, KRaft 모드로 Zookeeper 의존성을 제거했다.
- 기본 전달 보장은 **At-least-once**이고, 중복은 멱등성으로 해결한다.
- chap044는 `orderId`를 파티션 키로 써서 주문 단위 순서를 보장한다.

Kafka의 기본을 이해했으면, 이제 "어떤 토픽을 언제 만들 것인가"라는 설계의 문제로 넘어간다. chap044에는 아홉 개의 토픽이 있는데, 왜 이만큼이고 왜 이름이 이런지, 다음 장에서 본다.

---

# 5장. 명령장과 소식지

## 5.1 비유로 보기 — 관아의 명령장과 마을의 소식지

마을에는 두 종류의 종이가 돈다.

**명령장**. 관아(오케스트레이터)가 특정 점포에게 "네가 이것을 하라"고 보내는 공문이다. 명령장에는 수신자가 정해져 있다. 명령장을 받은 점포는 **반드시** 이것을 처리해야 한다. 예를 들어 "상품 점포는 이 주문의 재고 1개를 차감하라"는 것.

**소식지**. 마을 게시판에 붙이는 공개 알림이다. 소식지에는 수신자가 따로 없다. "관심 있는 사람은 알아서 보라"는 것이다. 예를 들어 "오늘 12시에 '아무개'가 주문을 냈다"는 것.

두 종이의 차이를 정리하면 이렇다.

| 구분 | 명령장 | 소식지 |
|---|---|---|
| 발행자 의도 | "네가 이걸 해라" | "이런 일이 일어났다" |
| 수신자 | 특정 1명 (그룹) | 누구나, 관심 있는 이 |
| 발행자가 답을 기대하는가 | 예 (수행 결과) | 아니오 (그저 알림) |
| 이름의 시제 | **현재형/명령형** ("차감하라") | **과거형** ("차감되었다") |

이 두 종류를 명확히 구분하는 것이 **좋은 메시지 설계**의 시작이다. chap044는 이를 Kafka 토픽 이름으로 드러낸다.

## 5.2 개념으로 정의하기 — Command vs Event

### Command (명령)

명령은 **"누군가가 무엇을 하기를 원한다"**는 의도를 담은 메시지다.
- 방향성이 있다 (발행자 → 수신자).
- 실패 가능성이 있다.
- 결과로 **이벤트를 발행**해야 한다 (성공 또는 실패).
- 이름은 `<entity>-<verb>-command` 형태 (명령형 동사).

예: `product-decrease-command`, `order-cancel-command`, `delivery-create-command`.

### Event (이벤트)

이벤트는 **"무언가가 이미 일어났다"**는 사실을 알리는 메시지다.
- 방향성이 없다 (누구나 들을 수 있음).
- 이미 일어난 사실이므로 **실패나 재시도가 없다**.
- 이름은 `<entity>-<past-verb>-event` 형태 (과거형 동사).

예: `order-created-event`, `product-decreased-event`, `delivery-completed-event`.

### 왜 둘을 구분해야 하나

이 구분은 단순한 관례가 아니다. **아키텍처 결정**이다.

**이유 1. 책임의 방향이 다르다.**
- 명령: 발행자는 "수행되길 원한다". 수신자가 **책임**을 진다.
- 이벤트: 발행자는 "이미 일어났음을 알린다". 수신자는 **반응**을 선택한다.

**이유 2. 결합도가 다르다.**
- 명령: 발행자와 수신자가 일대일(또는 소수)로 묶인다.
- 이벤트: 발행자는 수신자를 모른다. **새로운 구독자**가 추가되어도 발행자는 변경되지 않는다.

**이유 3. 처리 방식이 다르다.**
- 명령: 멱등성이 **반드시** 필요하다(8장).
- 이벤트: 보통 자연스럽게 "이미 처리된 것"으로 흡수되므로 별도 멱등성이 필요 없다(9장에서 상세히).

**이유 4. 확장성이 다르다.**
- 명령은 보통 "순차적으로" 처리된다.
- 이벤트는 "병렬로" 처리되어도 된다.

### CQRS 한마디

**CQRS(Command Query Responsibility Segregation)**는 "쓰기(Command)와 읽기(Query)를 분리하라"는 원칙이다. chap044는 엄밀한 CQRS는 아니지만, Command/Event의 구분은 CQRS의 정신과 맞닿아 있다. 쓰기의 트리거(명령)와 쓰기의 결과(이벤트)를 구분함으로써, 시스템이 명확하게 설계된다.

## 5.3 chap044의 9개 토픽 전수표

chap044의 Saga에 사용되는 토픽은 **9개**다. 이 책 전체가 이 9개를 중심으로 돈다. 한눈에 보자.

| # | 토픽 이름 | 종류 | 발행자 | 구독자 | 의미 |
|---|---|---|---|---|---|
| 1 | `order-created-event` | **Event** | Order | Orchestrator | 주문이 새로 생성되었음을 알림 (Saga 시작) |
| 2 | `product-decrease-command` | **Command** | Orchestrator | Product | 재고를 차감하라 |
| 3 | `product-decreased-event` | **Event** | Product | Orchestrator | 재고 차감이 시도되었음(성공/실패) |
| 4 | `product-increase-command` | **Command** | Orchestrator | Product | 재고를 복구하라 (보상) |
| 5 | `delivery-create-command` | **Command** | Orchestrator | Delivery | 배송을 준비하라 |
| 6 | `delivery-created-event` | **Event** | Delivery | Orchestrator | 배송 생성이 시도되었음(성공/실패) |
| 7 | `delivery-completed-event` | **Event** | Delivery | Orchestrator | 배송이 완료되었음 (관리자 API 호출 결과) |
| 8 | `order-complete-command` | **Command** | Orchestrator | Order | 주문을 완료 상태로 바꿔라 |
| 9 | `order-cancel-command` | **Command** | Orchestrator | Order | 주문을 취소 상태로 바꿔라 (보상) |

이 표에서 다음과 같은 패턴이 보인다.
- **Command는 모두 Orchestrator가 발행**한다. Orchestrator가 유일한 명령권자다.
- **Event는 비즈니스 서비스들이 발행**한다(Order, Product, Delivery).
- **Orchestrator는 모든 Event를 구독**한다. 비즈니스 서비스는 자기 관련 Command만 구독한다.
- Command 수: **5개**(2, 4, 5, 8, 9) — 모두 `~-command`로 끝남.
- Event 수: **4개**(1, 3, 6, 7) — 모두 `~-event`로 끝남.

### 쌍을 이루는 Command와 Event

대부분의 Command는 결과로 **대응되는 Event**를 발생시킨다.

| Command | 결과 Event |
|---|---|
| `product-decrease-command` | `product-decreased-event` |
| `delivery-create-command` | `delivery-created-event` |
| `product-increase-command` | (없음) |
| `order-complete-command` | (없음) |
| `order-cancel-command` | (없음) |

후자 세 가지(증가, 완료, 취소)는 **이벤트를 발행하지 않는다**. 왜? **누구도 그 결과에 반응할 필요가 없기 때문**이다.

- 재고 복구(increase)는 보상이다. 복구가 끝나면 Saga의 책임은 끝났다. 더 이상 반응할 것이 없다.
- 주문 완료(complete)와 주문 취소(cancel)는 **Saga의 마지막 단계**다. Saga 종점에서는 이벤트를 발행해도 구독할 주체가 없다(사용자에게는 웹소켓으로 직접 푸시).

이 선택은 설계 철학을 보여준다. **이벤트는 "필요한 구독자가 있을 때만" 만든다.** 만약 나중에 "주문 완료 시 마케팅 서비스에 알려야 한다"는 요구사항이 생기면, 그때 `order-completed-event`를 추가하면 된다.

### 💻 코드 스냅샷 ④ — 명령 DTO와 이벤트 DTO의 차이

명령과 이벤트의 구분은 **DTO 필드 구성에도 반영**된다. 한 쌍을 비교해보자.

**Command: `ProductDecreaseCommand`**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDecreaseCommand {
    private String messageId;   // 결정적 ID - 멱등성을 위해
    private Integer orderId;
    private Integer productId;
    private Integer quantity;
}
```

- `messageId`를 **맨 앞에** 둔다. 이것은 "이 명령은 멱등성 처리가 필요하다"는 선언이다.
- 명령의 재료가 모두 들어간다(무엇을, 얼마나).

**Event: `ProductDecreasedEvent`**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDecreasedEvent {
    private Integer orderId;
    private Integer productId;
    private boolean success;    // 성공/실패를 담는다
}
```

- `messageId`가 **없다**. 이벤트는 멱등성 처리 대상이 아니기 때문이다.
- `success` 필드를 통해 **결과 상태**를 담는다. 실패일 경우 보상 Saga가 트리거된다.

이 미세한 차이가 **책 전체를 관통하는 원칙**이다.

## 5.4 네이밍 규칙 상세

토픽 이름은 단순한 문자열이 아니다. **아키텍처 문서**다. chap044가 따르는 네이밍 규칙을 엄격하게 정의하면 이렇다.

### Command: `<entity>-<verb>-command`

- `<entity>`: 명령의 **대상 엔티티** (product, order, delivery).
- `<verb>`: **명령형 현재 동사** (decrease, increase, create, complete, cancel).
- 접미사: 항상 `-command`.

예:
- `product-decrease-command` ✅
- `delivery-create-command` ✅
- `product-reduced-command` ❌ — 과거형은 Command에 쓰지 않는다.

### Event: `<entity>-<past-verb>-event`

- `<entity>`: 이벤트가 일어난 **대상 엔티티**.
- `<past-verb>`: **과거 분사** (created, decreased, completed).
- 접미사: 항상 `-event`.

예:
- `order-created-event` ✅
- `product-decreased-event` ✅
- `order-create-event` ❌ — 현재형은 Event에 쓰지 않는다.

### 왜 이 규칙을 엄격히 지키나

이 규칙 덕분에 **토픽 이름만 봐도 그 본질을 안다**.

- `order-create-command`를 보면: "아, 누군가 Orchestrator가 Order에게 '만들라'고 시키는구나"
- `order-created-event`를 보면: "아, Order가 이미 만들어졌다는 사실이 방송되는구나"

이 구별 없이 둘 다 `order-create`로 통일해버리면, 수많은 **암묵적 질문**이 생긴다.
- "이건 누가 보내는 거야?"
- "이 메시지를 받으면 뭔가 해야 해, 아니면 그냥 알면 돼?"
- "실패하면 어떻게 되는 거야?"

이 질문들은 모두 문서를 뒤져서 답을 찾아야 한다. 네이밍 규칙은 이 질문들을 **토픽 이름 자체**에 박아 넣는다.

## 5.5 토픽 설계 안티 패턴 네 가지

### 안티패턴 1. 하나로 통합된 "신 토픽(God Topic)"

```
토픽: order-events
메시지 예: {"type":"CREATED",...}, {"type":"DECREASED",...}, {"type":"CANCELLED",...}
```

하나의 토픽에 **여러 이벤트 타입**을 담는 설계다. 얼핏 편리해 보이지만 재앙이다.

문제점:
- 구독자는 관심 없는 메시지까지 모두 받아서 `type`을 보고 걸러야 한다.
- 병렬 처리 단위가 커져 성능 불균형이 생긴다.
- 스키마 변경이 **다른 이벤트 타입**까지 영향을 준다.

해결: 이벤트 타입마다 **별도 토픽**을 만든다. chap044가 9개 토픽을 두는 이유다.

### 안티패턴 2. 지시대명사(Demonstrative) 토픽

```
토픽: order-update
```

"뭔가 업데이트되었다". 뭐가? 어떻게? 토픽 이름이 **정보를 감추고** 있다. 구독자가 로직을 위해 메시지 안의 `action` 필드를 또 봐야 한다.

해결: 구체적으로 `order-completed-event`, `order-cancelled-event` 등으로 분해한다.

### 안티패턴 3. 양방향(Bi-directional) 토픽

```
토픽: order-product-channel
```

양쪽이 모두 publish하고 모두 subscribe하는 구조다. 누가 무엇을 할 책임인지 흐려진다. 순환 구독이 생길 수도 있다.

해결: 각 방향마다 **독립적 토픽**을 만든다. "Order → Product 명령"과 "Product → Order 응답"은 각각의 토픽이다.

### 안티패턴 4. 과밀(Over-specialized) 토픽

```
토픽: product-decrease-for-order-type-regular-command
```

조건이 너무 세밀해서 토픽 수가 폭발한다. 이런 경우 토픽 관리·모니터링·파티션 조정이 악몽이 된다.

해결: **비즈니스 액션 수준**에서 토픽을 나누고, 세부 조건은 **메시지 페이로드**에 담는다. `product-decrease-command` 하나로 두고 페이로드에 `orderType`을 넣으면 된다.

### 설계 체크리스트

토픽을 새로 만들기 전에 다음을 물어보자.
1. 이 토픽의 메시지는 **명령인가 이벤트인가**? 이름의 시제가 맞나?
2. 발행자는 **한 명**인가? 여럿이면 책임이 흐려진다.
3. 구독자는 **누구인가**? 아무도 없으면 토픽을 만들 이유가 없다.
4. 페이로드에 `type`이나 `action` 필드가 있나? 있다면 그 필드대로 **토픽을 쪼개는 게** 나을 수 있다.
5. 토픽을 바꾸면 **몇 개 서비스에 영향**이 가는가? 많을수록 설계가 문제다.

## 5.6 시뮬레이터로 확인하기

두 번째 탭에서 Kafka 박스 안쪽을 자세히 보자. 토픽 슬롯마다 이름표가 붙어 있다.

"▶ Happy Path"를 누르고 **각 토픽 슬롯**을 주의 깊게 보자.
- 어떤 슬롯은 **노란색**(Event)이고
- 어떤 슬롯은 **주황색**(Command)이다.

(시뮬레이터 색 코드는 구현에 따라 다를 수 있지만, 9개 토픽의 흐름만 정확히 따라가면 이 장의 배움은 완성된다.)

## 5.7 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- Command는 "해라"고, Event는 "일어났다"다.
- 이름은 시제로 구분한다: `~-command`(명령형), `~-event`(과거형).
- chap044는 9개 토픽(5 Command + 4 Event)을 쓴다.
- Orchestrator가 모든 Command를 발행하고 모든 Event를 구독한다.
- 모든 Command는 결정적 `messageId`를 갖고, 모든 Event는 그것이 없다.
- 안티패턴: 신 토픽, 지시대명사 토픽, 양방향 토픽, 과밀 토픽.

토픽의 설계를 봤으니 이제 그 설계를 움직이는 **지휘자**를 만날 차례다. 지휘자가 왜 필요한지, 그리고 왜 "지휘"라는 이름을 갖는지, 다음 장에서 이야기한다.

---

# 6장. 지휘자의 등장

## 6.1 비유로 보기 — 눈치 보기와 지휘자

30명 규모의 실내악단이 있다. 한 번도 함께 연습한 적이 없다. 이들이 악보를 받아들고 처음 공연한다면 두 가지 방법 중 하나를 택할 수밖에 없다.

**방법 1: 눈치 보기.** "첫 번째 바이올린이 시작하면 나도 시작한다. 첼로가 강하게 치면 나도 강하게 친다. 옆을 보면서 호흡을 맞춘다." 이 방식은 작은 규모에서는 가능하지만, 30명이 동시에 서로를 보고 있을 수는 없다. 또한 **악곡의 전체 진행**을 아는 사람이 없다. 누군가가 삑사리를 내면 전체가 무너진다.

**방법 2: 지휘자.** 무대 위에 **지휘자 한 명**이 선다. 지휘자는 악곡의 전체 흐름을 안다. 바이올린에게 "여기서 솔로", 첼로에게 "여기서 크게", 금관에게 "여기서 조용히"를 **지시**한다. 연주자들은 자기 악기에만 집중한다. 전체 흐름은 지휘자의 책임이다.

두 방법에는 각각 장단점이 있다.

| 구분 | 눈치 보기 | 지휘자 |
|---|---|---|
| 장점 | 지휘자가 없어도 됨. 중앙 의존성 없음. | 전체 흐름이 명확. 책임 소재 명확. |
| 단점 | 전체 흐름을 아는 사람이 없음. 디버깅 어려움. | 지휘자가 쓰러지면 전체 정지. 지휘자가 병목. |
| 적합한 규모 | 3~4명의 소규모 | 10명 이상의 중·대규모 |

이 두 방식이 바로 **사가 패턴의 두 스타일**이다. 눈치 보기는 **코레오그래피(Choreography)**, 지휘자는 **오케스트레이션(Orchestration)**. 이 둘은 서로 다른 철학이다.

## 6.2 개념으로 정의하기 — 분산 트랜잭션과 사가 패턴

### 왜 "분산 트랜잭션"이 문제인가

모놀리식에서는 트랜잭션이 쉽다. `@Transactional` 한 줄로 데이터베이스 트랜잭션이 시작되고, 예외가 나면 자동 롤백된다. 이것이 **ACID**의 힘이다.

- **A**tomicity(원자성): 모두 성공하거나 모두 실패한다.
- **C**onsistency(일관성): 제약조건이 깨지지 않는다.
- **I**solation(격리성): 동시 트랜잭션이 서로 간섭하지 않는다.
- **D**urability(지속성): 커밋된 변경은 영구적이다.

그런데 MSA에서는 이 ACID가 여러 서비스·여러 DB에 걸쳐 있다. 주문을 만드는 작업은:
- Order DB에 주문 행 삽입 (OrderService)
- Product DB에서 재고 차감 (ProductService)
- Delivery DB에 배송 행 삽입 (DeliveryService)

이 셋을 **동시에 커밋**하려면 어떻게 해야 할까?

### 2PC의 한계

**2PC(Two-Phase Commit)**라는 기법이 있다. 트랜잭션 코디네이터가 모든 참여자에게 "준비 됐냐?"를 묻고(Phase 1), 모두 "됐다"고 응답하면 "커밋해라"를 보낸다(Phase 2).

이 방식은 ACID를 보장하지만, 다음 이유로 **MSA에서는 거의 쓰지 않는다**:

1. **블록킹**: Phase 1에서 모든 참여자가 응답할 때까지 다른 트랜잭션이 대기해야 한다. 한 서비스가 느리면 전체가 느려진다.
2. **가용성 저하**: 참여자가 하나라도 죽으면 전체가 멈춘다.
3. **구현 복잡성**: 코디네이터·참여자 모두 2PC 프로토콜을 지원해야 한다.
4. **Kafka 같은 메시지 브로커와 맞지 않음**: 2PC는 기본적으로 RDBMS 간 조정을 위한 것이다.

### BASE 패러다임

MSA는 ACID 대신 **BASE**를 선택한다.

- **B**asically **A**vailable: 항상 응답한다 (늦게라도).
- **S**oft state: 상태는 일시적으로 불일치할 수 있다.
- **E**ventual consistency: 결국에는 일관성에 도달한다.

"일시적 불일치를 허용하되, 시간이 지나면 반드시 일관성에 도달한다"는 것이 BASE의 핵심이다. 이것을 가능케 하는 구체적 설계 패턴이 바로 **사가(Saga)**다.

### Saga 패턴 — 분산 트랜잭션을 조각내기

**Saga**는 한 비즈니스 트랜잭션을 **여러 로컬 트랜잭션의 연쇄**로 나눈다. 각 로컬 트랜잭션은 자기 서비스 안에서 완결되고, 성공하면 다음 로컬 트랜잭션을 **트리거**한다.

"주문 생성"이라는 비즈니스 트랜잭션을 Saga로 풀면 이렇게 된다.
1. Order 서비스: 주문 삽입 (로컬 트랜잭션 1)
2. Product 서비스: 재고 차감 (로컬 트랜잭션 2)
3. Delivery 서비스: 배송 생성 (로컬 트랜잭션 3)
4. Order 서비스: 상태 변경 (로컬 트랜잭션 4)

각 단계 사이는 **Kafka 메시지**로 연결된다. 한 단계가 성공하면 다음 단계의 명령을 메시지로 보낸다. 하나라도 실패하면 **보상 트랜잭션**이 시작된다. (보상의 이야기는 7장이다.)

### Saga의 두 방식: Choreography vs Orchestration

#### Choreography (코레오그래피)

**서비스끼리 서로의 이벤트를 직접 듣고 반응**한다. 중앙 조정자가 없다.

```
Order ──order-created-event──► (Product 구독)
                                Product가 재고 차감
                                Product ──product-decreased-event──► (Delivery 구독)
                                                                     Delivery가 배송 생성
                                                                     Delivery ──delivery-created-event──► (Order 구독)
                                                                                                          Order가 상태 변경
```

장점:
- 중앙 의존성이 없어 **단순한 아키텍처**.
- 새 서비스를 **독립적으로 추가**할 수 있다 (그저 이벤트만 구독하면 됨).

단점:
- **전체 흐름을 아는 주체가 없다**. 개발자가 여러 서비스 코드를 돌아다니며 "이 이벤트를 받으면 무엇이 일어나지?"를 재구성해야 한다.
- **실패 처리가 악몽**. 3단계에서 실패했을 때, 누가 어떻게 1·2단계를 롤백할지 추적하기 어렵다.
- **순환 의존성**이 쉽게 생긴다. A가 B의 이벤트를 듣고, B가 A의 이벤트를 듣는 구조가 되면 디버깅 불가능에 가까워진다.

#### Orchestration (오케스트레이션)

**중앙의 지휘자(Orchestrator)**가 모든 이벤트를 받고, 모든 다음 단계를 **명령으로 지시**한다.

```
Order ──order-created-event──► Orchestrator
                                    │
                                    ▼
                               ──product-decrease-command──► Product
                                    
Product ──product-decreased-event──► Orchestrator
                                         │
                                         ▼
                                    ──delivery-create-command──► Delivery

Delivery ──delivery-created-event──► Orchestrator
                                          │
                                          ▼
                                     (대기 또는 완료 명령)
```

장점:
- **전체 흐름이 한 곳에 집중**되어 있다. Orchestrator 코드만 보면 Saga 전체를 이해한다.
- **실패 처리가 명확**. Orchestrator가 보상 명령을 순서대로 발행하면 된다.
- **추적이 쉽다**. Saga의 상태를 Orchestrator가 기록하므로 모니터링이 간단.

단점:
- **Orchestrator가 단일 장애점**. Orchestrator가 죽으면 새 Saga가 시작 안 된다.
- **결합도가 올라간다**. 새 서비스를 추가하려면 Orchestrator 코드를 수정해야 한다.
- **Orchestrator가 비대**해지기 쉽다. 비즈니스 로직이 점점 몰린다.

### 어느 쪽이 좋은가 — 교육적 답과 현실적 답

| 상황 | 추천 |
|---|---|
| 단순한 2~3단계 흐름 | **Choreography** |
| 복잡한 다단계 흐름 (4단계 이상) | **Orchestration** |
| 실패 시나리오가 많다 | **Orchestration** |
| 여러 팀이 독립적으로 서비스를 개발 | **Choreography** |
| 하나의 팀이 전체를 관리 | **Orchestration** |

chap044는 **Orchestration**을 선택했다. 이유는 두 가지다.
1. **교육용이라 "전체 흐름"을 한 파일에서 볼 수 있어야** 한다. Choreography로 쓰면 학습자가 4개 서비스를 돌아다녀야 한다.
2. **실패 시나리오가 많다** (재고 부족, 배송 생성 실패, 관리자 취소 등). Orchestration이 이들을 체계적으로 관리하기 쉽다.

### Saga 상태 머신

Orchestration Saga는 본질적으로 **상태 머신(State Machine)**이다. 각 상태에서 특정 이벤트를 받으면, 다른 상태로 전이하면서 명령을 발행한다.

chap044의 상태 머신은 다음과 같다.

```
       ┌─────────────────────────────────────────────┐
       ▼                                             │
[INIT]                                               │
  │ order-created-event 수신                          │
  ▼                                                  │
[재고 차감 대기]                                      │
  │ product-decreased-event(success) 수신             │
  ▼                                                  │
[배송 생성 대기]                                      │
  │ delivery-created-event(success) 수신              │
  ▼                                                  │
[배송 완료 대기] ── delivery-completed-event ──► [COMPLETED]
                                                     │
                                                     └─► 상태 초기화

* 실패 경로는 7장에서 상세 다룸
```

각 상태에서 Orchestrator가 하는 일은 **세 줄**로 요약된다.
1. 이벤트를 받는다.
2. `states` Map에서 해당 주문의 상태를 꺼낸다.
3. 다음 명령을 발행하거나 상태를 정리한다.

## 6.3 chap044에선 이렇게 한다

### Orchestrator의 구조

Orchestrator는 **하나의 클래스**다. `OrderOrchestrator.java`에 있다. 이 클래스는 다음을 갖는다.

- `KafkaTemplate`: 명령 발행용.
- `states`: `ConcurrentHashMap<Integer, WorkflowState>`. 진행 중인 Saga의 상태를 저장.
- `@KafkaListener` 메서드 4개: `order-created-event`, `product-decreased-event`, `delivery-created-event`, `delivery-completed-event`를 각각 구독.

이 네 메서드가 **Saga 상태 머신의 전이**를 구현한다.

### 결정적 messageId의 탄생

모든 Command에는 `saga-{orderId}-{action}` 형태의 **결정적 messageId**가 붙는다.

- `saga-42-decrease` (재고 차감)
- `saga-42-create-delivery` (배송 생성)
- `saga-42-complete` (주문 완료)
- `saga-42-cancel` (주문 취소)
- `saga-42-increase` (재고 복구, 보상)

이 ID가 왜 **결정적**이어야 하는가? 두 가지 이유다.

1. **재시도 시 같은 ID**. Orchestrator가 재시작되거나 같은 명령을 두 번 발행해도, `messageId`는 같다. 수신자는 PK 중복으로 감지해서 중복 처리를 차단한다 (8장).
2. **추적 가능성**. 로그에서 `saga-42`로 검색하면 42번 주문에 얽힌 모든 명령을 한 번에 볼 수 있다.

만약 `messageId`로 **UUID 같은 랜덤 값**을 쓰면:
- 같은 명령을 두 번 발행하면 UUID가 달라서 **중복 감지 실패**.
- 중복 없애는 책임이 수신자에서 **발행자**(중복 발행 안 함)로 옮겨가는데, 비동기 재시도 환경에서 이 보장은 어렵다.

결정적 ID는 **발행자와 수신자 간의 계약**이다. 발행자는 "같은 의도의 명령은 항상 같은 ID"를 약속하고, 수신자는 "같은 ID의 명령은 한 번만 처리"를 약속한다.

### 상태의 보존 — WorkflowState

`WorkflowState`는 Saga가 진행되는 동안 필요한 **컨텍스트**를 담는다.

```java
@Data
private static class WorkflowState {
    private final int orderId;
    private final Integer productId;
    private final Integer quantity;
    private final String address;
}
```

왜 이것이 필요한가? Orchestrator의 각 `@KafkaListener` 메서드가 받는 이벤트는 **부분 정보**만 갖기 때문이다. 예를 들어 `product-decreased-event`는 `orderId`와 `productId`만 알 뿐, **주소**는 모른다. 그런데 다음 단계(배송 생성)는 주소가 필요하다.

그래서 Orchestrator는 Saga 시작 시점에 **주문의 모든 컨텍스트**를 `states`에 저장해두고, 이후 이벤트 수신 시 꺼내 쓴다.

```java
@KafkaListener(topics = "order-created-event", groupId = "orchestrator")
public void onOrderCreated(OrderCreatedEvent event) {
    int orderId = event.getOrderId();
    states.put(orderId, new WorkflowState(
        orderId, event.getProductId(),
        event.getQuantity(), event.getAddress()));
    // 재고 차감 명령 발행
    send("product-decrease-command", orderId,
        new ProductDecreaseCommand(
            "saga-" + orderId + "-decrease",
            orderId, event.getProductId(), event.getQuantity()));
}
```

Saga가 **완료되거나 취소되면** `states.remove(orderId)`로 해당 주문의 상태를 지운다. 이것은 **메모리 누수 방지**를 위해 중요하다.

### In-memory 상태의 한계

`ConcurrentHashMap`은 **Orchestrator Pod의 메모리** 안에 있다. 이 말은:
- Orchestrator가 **재시작되면** 진행 중이던 Saga의 상태가 모두 **사라진다**.
- 재시작 후 들어오는 후속 이벤트(`product-decreased-event` 등)는 `states.get()`이 null을 반환해서 조용히 **무시**된다.
- 진행 중이던 주문은 **영원히 미완성 상태**로 남는다.

이것은 chap044의 **치명적 한계**다. 교육용이라 용납되지만, 프로덕션에서는 절대 허용 안 된다. 해결책은 다음 중 하나다.

1. **Redis 같은 외부 상태 저장소**에 `WorkflowState`를 저장.
2. **데이터베이스 테이블**에 `saga_state_tb`를 만들어 저장.
3. **Saga Framework**(Axon, Spring State Machine 등)를 도입.

13장(프로덕션으로 가려면)에서 이 주제를 다시 다룬다.

### 💻 코드 스냅샷 ⑤ — 상태 머신의 본체

`orchestrator/src/main/java/com/metacoding/orchestrator/handler/OrderOrchestrator.java` (발췌)

```java
// 1) 주문 생성 → 재고 차감 명령
@KafkaListener(topics = "order-created-event", groupId = "orchestrator")
public void onOrderCreated(OrderCreatedEvent event) {
    int orderId = event.getOrderId();
    states.put(orderId, new WorkflowState(orderId, event.getProductId(),
            event.getQuantity(), event.getAddress()));
    send("product-decrease-command", orderId,
        new ProductDecreaseCommand("saga-" + orderId + "-decrease",
            orderId, event.getProductId(), event.getQuantity()));
}

// 2) 재고 차감 결과 → 성공: 배송 생성 / 실패: 주문 취소
@KafkaListener(topics = "product-decreased-event", groupId = "orchestrator")
public void onProductDecreased(ProductDecreasedEvent event) {
    int orderId = event.getOrderId();
    WorkflowState state = states.get(orderId);
    if (state == null) return;
    if (event.isSuccess()) {
        send("delivery-create-command", orderId,
            new DeliveryCreateCommand("saga-" + orderId + "-create-delivery",
                orderId, state.getAddress()));
    } else {
        send("order-cancel-command", orderId,
            new OrderCancelCommand("saga-" + orderId + "-cancel", orderId));
        states.remove(orderId);
    }
}
```

이 10여 줄이 chap044 **Saga의 핵심**이다. 나머지 두 개의 메서드(`onDeliveryCreated`, `onDeliveryCompleted`)도 같은 패턴을 따른다.

주목할 세 가지 패턴:
1. **`states.get(orderId)` → null 체크**. 이미 완료/취소된 Saga의 뒤늦은 이벤트를 조용히 무시한다.
2. **`event.isSuccess()`로 분기**. 이벤트의 `success` 플래그로 성공/실패 경로를 나눈다.
3. **결정적 messageId 부여**. `saga-{orderId}-{action}` 패턴으로 항상 일관된 ID.

## 6.4 Happy Path — 9단계 상세 추적

사용자 ssar이 MacBook Pro 1개를 "Seoul" 주소로 주문한다. 주문 ID는 42번이라고 하자.

### 단계별 시퀀스 다이어그램

```
사용자      Order       Kafka              Orchestrator    Product     Delivery
  │          │            │                     │             │           │
  │─주문──►│            │                     │             │           │
  │          │─insert───►                     │             │           │
  │          │  (PENDING) │                    │             │           │
  │          │─────publish: order-created─────►│            │           │
  │◄완료─────│            │                     │             │           │
  │                        │                    │             │           │
  │                        │              (state.put 42)      │           │
  │                        │              ──publish: product-decrease───►│ (no, 방향 틀림)
  │                        │─────publish: product-decrease-command───────►│
  │                        │                    │             │ (차감) │
  │                        │                    │             │  10→9   │
  │                        │◄─publish: product-decreased (success)──────│
  │                        │                    │             │           │
  │                        │              (다음 단계 결정)    │           │
  │                        │─────publish: delivery-create-command────────►│
  │                        │                    │             │            │ (생성)
  │                        │◄─publish: delivery-created (success)────────│
  │                        │                    │             │           │
  │                        │              (배송 완료 API 대기)             │
  │          [관리자가 PUT /api/deliveries/{id}/complete]                 │
  │                        │                    │             │            │ (상태 변경)
  │                        │◄─publish: delivery-completed-event──────────│
  │                        │                    │             │           │
  │                        │─────publish: order-complete-command────────►│
  │                        │                    │             │   ↑
  │                        │                    │             │   └─ 주문 COMPLETED
  │◄─웹소켓: "주문 완료"───│                   │             │           │
```

### 각 단계의 세부 내용

#### 단계 1. 사용자가 "주문하기" 클릭

- 브라우저 → `POST /api/orders { productId: 1, quantity: 1, address: "Seoul" }`
- 헤더: `Authorization: Bearer <jwt>`
- 게이트웨이 → Order 서비스로 라우팅

#### 단계 2. Order 서비스가 주문 삽입

- JWT 검증 → userId 추출 (예: 1번)
- `INSERT INTO order_tb (user_id, product_id, quantity, status) VALUES (1, 1, 1, 'PENDING')`
- 생성된 order.id = 42

#### 단계 3. Order가 `order-created-event` 발행

- 페이로드: `{orderId: 42, userId: 1, productId: 1, quantity: 1, address: "Seoul"}`
- 즉시 사용자에게 HTTP 200 응답

#### 단계 4. Orchestrator가 이벤트 수신 → 상태 저장 → 재고 차감 명령

- `states.put(42, new WorkflowState(42, 1, 1, "Seoul"))`
- `product-decrease-command` 발행:
  `{messageId: "saga-42-decrease", orderId: 42, productId: 1, quantity: 1}`

#### 단계 5. Product가 명령 수신 → 재고 차감

- 멱등성 체크: `processed_message_tb`에 `saga-42-decrease` INSERT 시도 → 첫 수신이므로 성공
- 재고 조회: MacBook Pro → quantity = 10
- 10 >= 1이므로 차감: 10 → 9
- `UPDATE product_tb SET quantity = 9 WHERE id = 1`

#### 단계 6. Product가 `product-decreased-event` 발행

- 페이로드: `{orderId: 42, productId: 1, success: true}`

#### 단계 7. Orchestrator가 이벤트 수신 → 배송 생성 명령

- `states.get(42)` → `WorkflowState(42, 1, 1, "Seoul")` 꺼냄
- `delivery-create-command` 발행:
  `{messageId: "saga-42-create-delivery", orderId: 42, address: "Seoul"}`

#### 단계 8. Delivery가 명령 수신 → 배송 생성

- 멱등성 체크: `saga-42-create-delivery` INSERT 시도 → 첫 수신 성공
- 주소 검증: `"Seoul"`은 유효
- `INSERT INTO delivery_tb (order_id, address, status) VALUES (42, 'Seoul', 'READY')`
- 생성된 delivery.id = 17

#### 단계 9. Delivery가 `delivery-created-event` 발행 → Orchestrator 대기

- 페이로드: `{orderId: 42, deliveryId: 17, success: true}`
- Orchestrator는 이 이벤트를 받고 **아무 명령도 발행하지 않는다**. 관리자가 `/api/deliveries/17/complete`를 호출할 때까지 대기한다.

#### (대기) 관리자의 배송 완료 API 호출

- 관리자(실무에서는 배송 업체의 웹훅) → `PUT /api/deliveries/17/complete`
- Delivery 서비스: `UPDATE delivery_tb SET status = 'COMPLETED' WHERE id = 17`
- Delivery가 `delivery-completed-event` 발행: `{orderId: 42}`

#### 단계 10. Orchestrator → Order에 완료 명령

- `order-complete-command` 발행: `{messageId: "saga-42-complete", orderId: 42}`
- `states.remove(42)`로 상태 정리

#### 단계 11. Order가 명령 수신 → 상태 변경 + 웹소켓 푸시

- 멱등성 체크: `saga-42-complete` INSERT 성공
- `UPDATE order_tb SET status = 'COMPLETED' WHERE id = 42`
- 웹소켓 푸시: `/topic/orders/1`에 `{orderId: 42, status: "COMPLETED", message: "주문이 완료되었습니다."}`

사용자 화면에는 **"🎉 주문이 완료되었습니다"**가 나타난다. Saga가 성공적으로 끝났다.

### 이 Happy Path에서 주목할 점

1. **사용자는 단계 3에서 응답받는다.** 이후의 단계들은 백그라운드에서 일어난다. 사용자 체감 응답 시간 ≈ 0.2초.
2. **Orchestrator는 네 번의 이벤트를 받고 세 번의 명령을 발행한다.** 그것이 Orchestrator의 역할 전부다.
3. **각 비즈니스 서비스는 자기 도메인 로직만 실행한다.** Product는 재고만, Delivery는 배송만, Order는 주문 상태만.
4. **Kafka 메시지는 총 9개 오간다.** 4 Event + 5 Command.

## 6.5 시뮬레이터로 확인하기

두 번째 탭 "🛒 MSA 비즈니스 흐름"에서 **"⏭ 단계별 실행"** 모드를 켠다. 그리고 "▶ Happy Path"를 누른다.

첫 번째 단계를 누르면:
- 브라우저의 주문 폼에서 POST가 날아간다.
- Order 박스에 데이터가 들어가고, DB 패널의 `order_tb`에 새 행이 뜬다.
- Kafka의 `order-created-event` 슬롯에 노란 점이 나타난다.

다음 단계를 누르면:
- Orchestrator가 그 점을 집어간다.
- Kafka의 `product-decrease-command` 슬롯에 새 점이 나타난다.

이렇게 한 걸음씩 진행하면서, 이 장에서 설명한 9단계를 **눈으로 확인**하자. 중요한 관찰 포인트:
- **DB 패널의 `processed_message_tb` 행이 3번 추가된다** (decrease, create-delivery, complete).
- **`product_tb`의 quantity가 10 → 9로 변한다.**
- **`order_tb`의 status가 PENDING → COMPLETED로 변한다.**
- **마지막에 브라우저에 초록 알림**이 뜬다.

## 6.6 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 2PC는 블록킹·가용성 문제로 MSA에 부적합하다. 대신 BASE 패러다임을 쓴다.
- Saga는 분산 트랜잭션을 **여러 로컬 트랜잭션의 연쇄**로 쪼갠다.
- Saga는 두 스타일이 있다: Choreography(눈치 보기), Orchestration(지휘자).
- chap044는 Orchestration을 선택했고, 하나의 `OrderOrchestrator`가 전체를 관리한다.
- 모든 Command는 `saga-{orderId}-{action}` 형태의 결정적 ID를 갖는다.
- `WorkflowState`는 in-memory ConcurrentHashMap에 보관된다 (프로덕션에서는 외부 저장소 필요).
- Happy Path는 4 Event + 5 Command, 총 9개의 Kafka 메시지로 이루어진다.

그러나 이 모든 것이 성공했을 때의 이야기다. **하나라도 실패하면?** 그것이 다음 장의 주제다. Saga의 진짜 힘은 실패 처리에 있다.

---

# 7장. 지휘가 무너질 때

## 7.1 비유로 보기 — 악단의 사고

공연 중간에 사고가 난다. 몇 가지 사고 시나리오를 상상해보자.

**사고 1.** 바이올린 파트가 악보 2페이지의 어려운 구간에서 연주를 멈춘다. "죄송해요, 이 악보 못 하겠어요." 지휘자는 어떻게 해야 할까?

"잠깐, 그럼 첫 페이지부터 **다시** 시작할까?" 불가능하다. 음악은 **시간의 예술**이라 **되돌릴 수 없다**. 이미 연주한 첫 페이지는 "연주했다"는 사실이 남는다. 지휘자는 이 사고를 **앞으로의 처리**로만 수습할 수 있다.

**사고 2.** 바이올린 파트가 연주한 2페이지까지 가져갔는데, 첼로 파트가 "이거 조가 틀렸어요"라고 항의한다. 지휘자는:
- 바이올린에게 "당신 연주를 **되돌려놓으라**"고는 못 한다 (되돌릴 수 없으니).
- 대신 바이올린에게 "지금부터 2페이지를 **다시 치면서 조를 맞추라**"고 지시할 수 있다 (**보상 행위**).

이것이 실제 세계에서 분산 트랜잭션의 **보상(compensation)** 개념이다. 이미 일어난 일을 되돌리지는 못하지만, **그 일을 상쇄하는 반대 행위**를 할 수 있다.

- 재고를 차감했다면 → **재고를 복구**한다.
- 주문을 생성했다면 → **주문을 취소** 상태로 변경한다.
- 돈을 출금했다면 → **돈을 환불**한다.

보상은 완벽한 롤백이 아니다. 시간도 흘렀고, 고객은 이미 알림을 받았을 수도 있다. 그러나 **데이터의 일관성**은 회복할 수 있다. 이것이 Saga의 본질이다.

## 7.2 개념으로 정의하기 — 보상 트랜잭션의 설계 원칙

### Compensating Transaction이란

**보상 트랜잭션(Compensating Transaction)**은 "이미 커밋된 로컬 트랜잭션의 효과를 상쇄하는 또 다른 로컬 트랜잭션"이다.

성공한 정방향 트랜잭션마다 **대응되는 보상 트랜잭션**이 정의되어야 한다.

| 정방향 | 보상 |
|---|---|
| 재고 차감 | 재고 복구 |
| 배송 생성 | 배송 취소 |
| 주문 PENDING 삽입 | 주문 CANCELLED로 변경 |
| 결제 승인 | 결제 취소 |
| 쿠폰 사용 | 쿠폰 복구 |

보상은 **정방향의 반대 개념**이지 **정방향의 삭제**가 아니다. 예를 들어 재고를 차감하고 보상으로 복구하면, 재고 수는 원래대로 돌아오지만 **차감했다가 복구했다는 이력**은 남는다(물론 chap044는 이력을 남기지 않지만, 실무에서는 남겨야 한다).

### Saga 설계의 5가지 불변 규칙

이 책이 일관되게 강조하는 Saga 설계 원칙을 정리한다.

#### 규칙 1. 역순 보상

실패가 일어나면 **이미 성공한 단계들을 역순으로** 보상한다.

예를 들어 4단계 Saga에서 3단계가 실패하면:
- 성공한 것: 1단계, 2단계
- 실패한 것: 3단계
- 보상 순서: 2단계 보상 → 1단계 보상 (역순)

chap044의 경우, 배송 생성 실패 시:
- 성공한 것: (1) Order 주문 생성, (2) 재고 차감
- 실패한 것: 배송 생성
- 보상 순서: 재고 복구 → 주문 취소 (역순)

#### 규칙 2. 보상도 멱등적

보상 명령도 중복 수신될 수 있다. 멱등성이 **반드시** 필요하다.

chap044는 보상 명령도 결정적 `messageId`(`saga-{orderId}-increase`, `saga-{orderId}-cancel`)를 갖는다.

#### 규칙 3. 결정적 ID

앞서 6장에서 설명했듯이, 모든 명령은 `saga-{orderId}-{action}` 같은 결정적 ID를 갖는다. 보상 명령도 예외 없다.

#### 규칙 4. 실패 이벤트도 반드시 퍼블리시

"재고가 부족해서 차감 실패"인 경우, **실패했다는 사실 자체를 이벤트로 방송**해야 한다. chap044의 `product-decreased-event`가 `success: false`로 퍼블리시되는 이유다.

만약 실패 시 아무 이벤트도 발행 안 하면, Orchestrator는 **영원히 기다린다**. 이것을 **데드락(deadlock)** 또는 **행즈(hangs)**라고 부른다. 가장 피해야 할 Saga 버그 중 하나다.

#### 규칙 5. 지휘자의 순수성

Orchestrator는 **결정만** 한다. 비즈니스 로직(재고 차감 알고리즘, 배송 주소 검증 로직)은 담지 않는다. 그것은 비즈니스 서비스의 책임이다.

만약 Orchestrator에 "재고가 10개 이상이면 차감하고 아니면 실패" 같은 로직이 들어가면:
- Product 서비스의 도메인 로직이 **두 곳에 분산**된다.
- Orchestrator가 점점 비대해진다 (God Orchestrator).
- 서비스가 서로의 도메인에 간섭한다.

Orchestrator는 "이 결과에 대해 다음에 누구에게 무엇을 시킬 것인가"만 안다. 그것이 지휘의 순수성이다.

## 7.3 chap044의 실패 시나리오 3가지

chap044는 교육 목적으로 세 가지 실패 시나리오를 준비해두었다. 하나씩 **상세히 추적**해보자. 주문 ID를 42번이라고 하고, 각 시나리오를 Happy Path와 비교해본다.

### 시나리오 A. 재고 부족 — 초기 실패

사용자 ssar이 iPhone 15(productId=2, 재고=0) 1개를 주문.

#### 시퀀스 다이어그램

```
사용자      Order       Kafka              Orchestrator    Product     Delivery
  │          │            │                     │             │           │
  │─주문──►│            │                     │             │           │
  │          │─insert───►                     │             │           │
  │          │  (PENDING) │                    │             │           │
  │          │──────order-created-event──────►│             │           │
  │◄완료─────│            │                     │             │           │
  │                        │              (state.put)         │           │
  │                        │──product-decrease-command──────►│           │
  │                        │                    │             │(재고 0)   │
  │                        │                    │             │  실패!   │
  │                        │◄product-decreased(success=false)│           │
  │                        │                    │             │           │
  │                        │──order-cancel-command───────────┼──►Order   │
  │                        │                    │             │   │       │
  │                        │              (state.remove)      │   └→ CANCELLED
  │◄─웹소켓: "재고 부족 취소"──────────────────────────────────│           │
```

#### 각 단계의 상태 변화

| 단계 | `order_tb` | `product_tb` | `delivery_tb` | `processed_message_tb` |
|---|---|---|---|---|
| 주문 삽입 | `(42, 1, 2, 1, PENDING)` | iPhone 15: 0 | (없음) | (비어있음) |
| 재고 차감 시도 (실패) | 그대로 | 그대로 (차감 실패) | (없음) | `saga-42-decrease/product` |
| 주문 취소 명령 도착 | `status: PENDING→CANCELLED` | 그대로 | (없음) | `saga-42-decrease/product`, `saga-42-cancel/order` |

**포인트**:
- 주문은 PENDING으로 삽입됐다가 CANCELLED로 바뀐다. **삭제되지 않는다**. 이력이 남는다.
- 재고는 건드려지지 않았다(차감 시도만 했지 실제 차감 안 됨).
- 보상 Saga가 **재고 복구를 호출하지 않는다**. 이유: 재고 차감이 **실패**했으므로 복구할 것이 없다.
- 사용자는 웹소켓으로 "재고 부족으로 주문이 취소되었습니다" 알림을 받는다.

#### 오케스트레이터 코드 추적

```java
@KafkaListener(topics = "product-decreased-event", groupId = "orchestrator")
public void onProductDecreased(ProductDecreasedEvent event) {
    int orderId = event.getOrderId();
    WorkflowState state = states.get(orderId);
    if (state == null) return;
    if (event.isSuccess()) {
        // 이 분기 아님
    } else {
        // 여기로 진입
        send("order-cancel-command", orderId,
            new OrderCancelCommand("saga-" + orderId + "-cancel", orderId));
        states.remove(orderId);
    }
}
```

### 시나리오 B. 배송 생성 실패 — 중간 실패 (보상 필요)

사용자가 MacBook Pro 1개를 **빈 주소 `""`**로 주문(클라이언트 검증이 우회됐다고 가정).

#### 시퀀스 다이어그램

```
사용자      Order       Kafka              Orchestrator    Product     Delivery
  │          │            │                     │             │           │
  │─주문──►│            │                     │             │           │
  │          │──────order-created-event──────►│             │           │
  │◄완료─────│            │                     │             │           │
  │                        │              (state.put)         │           │
  │                        │──product-decrease-command──────►│           │
  │                        │                    │             │(차감 OK)  │
  │                        │                    │             │ 10→9     │
  │                        │◄product-decreased(success=true)│           │
  │                        │                    │             │           │
  │                        │──delivery-create-command────────┼──────────►│
  │                        │                    │             │           │(주소 빈
  │                        │                    │             │           │ 문자열
  │                        │                    │             │           │ → 실패)
  │                        │◄delivery-created(success=false)──────────────│
  │                        │                    │             │           │
  │                        │  *** 보상 시작 ***                │           │
  │                        │──product-increase-command──────►│           │
  │                        │                    │             │(재고 복구)│
  │                        │                    │             │  9→10    │
  │                        │──order-cancel-command────────┐  │           │
  │                        │                    │        ▼  │           │
  │                        │                               Order: CANCELLED
  │                        │              (state.remove)     │           │
  │◄─웹소켓: "재고 부족 취소"──────────────────────────────────────│           │
```

#### 각 단계의 상태 변화

| 단계 | `order_tb` | `product_tb` (MacBook) | `delivery_tb` | `processed_message_tb` |
|---|---|---|---|---|
| 주문 삽입 | PENDING | 10 | (없음) | (비어있음) |
| 재고 차감 OK | PENDING | 9 | (없음) | `saga-42-decrease/product` |
| 배송 생성 실패 | PENDING | 9 | (없음, 미생성) | 위에 추가로 `saga-42-create-delivery/delivery` |
| **보상 1: 재고 복구** | PENDING | **10 (복구)** | (없음) | 위에 추가로 `saga-42-increase/product` |
| **보상 2: 주문 취소** | **CANCELLED** | 10 | (없음) | 위에 추가로 `saga-42-cancel/order` |

**포인트**:
- 재고가 **차감되었다가 복구**되었다. 최종적으로 재고는 그대로지만, 이력상 "움직임이 있었다".
- `processed_message_tb`에 **4개 메시지 ID**가 쌓인다. 보상 메시지도 포함됨.
- 보상 순서: **재고 복구 먼저, 주문 취소 나중**. 이것은 역순 원칙의 적용.

#### 오케스트레이터 코드 추적

```java
@KafkaListener(topics = "delivery-created-event", groupId = "orchestrator")
public void onDeliveryCreated(DeliveryCreatedEvent event) {
    int orderId = event.getOrderId();
    WorkflowState state = states.get(orderId);
    if (state == null) return;

    if (event.isSuccess()) {
        // 성공 시: 아무 명령 발행 안 함 (배송 완료 API 대기)
        return;
    }

    // 실패 시: 두 개의 보상 명령 연속 발행
    send("product-increase-command", orderId,
        new ProductIncreaseCommand("saga-" + orderId + "-increase",
            orderId, state.getProductId(), state.getQuantity()));
    send("order-cancel-command", orderId,
        new OrderCancelCommand("saga-" + orderId + "-cancel", orderId));
    states.remove(orderId);
}
```

**주목**: 두 명령을 **연속으로** 발행한다. 순서는 Kafka 메시지 순서대로 보장된다(같은 키 = 같은 파티션).

### 시나리오 C. 취소 시나리오(삭제) — chap044에는 없지만 논리 설명

chap044는 배송 완료 후 취소를 구현하지 않았다. 그러나 프로덕션에서 자주 일어나는 시나리오이므로 개념적으로 다룬다.

이미 배송이 시작된 뒤 사용자가 취소하려면:
- **배송 복구**: 배송 업체에 "회수"를 요청한다.
- **재고 복구**: 회수된 상품을 재고로 되돌린다.
- **주문 취소**: 주문을 CANCELLED로 변경.
- **환불**: 결제 쪽도 있다면 환불 트랜잭션.

각각이 **모두 시간이 걸린다**. Saga는 이 **느린 보상**까지 수용해야 한다. 각 단계는 또 다시 실패할 수 있고, 실패하면 **더 복잡한 보상의 보상(nested compensation)**이 필요하다.

이것이 "Saga는 비싸다"는 말이 나오는 지점이다. 복잡한 보상 체인은 코드도 복잡하고 테스트도 복잡하다. 그러나 분산 트랜잭션의 피할 수 없는 대가다.

## 7.4 Saga 설계의 5가지 불변 규칙 재정리

7.2에서 언급한 다섯 규칙을 chap044의 구체적 코드와 매핑해보자.

### 규칙 1. 역순 보상 — chap044 어디에 있나

`OrderOrchestrator.onDeliveryCreated`의 실패 분기:
```java
send("product-increase-command", ...);  // 먼저: 재고 복구
send("order-cancel-command", ...);       // 나중: 주문 취소
```

**역순**이다. 성공 순서는 (재고 차감 → 배송 생성)이었고, 보상은 (배송 취소 → 재고 복구)가 되어야 하지만, 배송은 **생성 자체가 실패**했으므로 취소할 것이 없다. 따라서 실제 보상은 (재고 복구 → 주문 취소) 순이 된다.

### 규칙 2. 보상도 멱등적 — chap044 어디에 있나

`ProductHandler.onIncrease`:
```java
@KafkaListener(topics = "product-increase-command", groupId = "product-service")
@Transactional
public void onIncrease(ProductIncreaseCommand cmd) {
    if (isDuplicate(cmd.getMessageId())) return;  // 멱등성 체크!
    // ... 복구 로직
}
```

보상 명령도 `isDuplicate` 체크를 **맨 처음** 통과해야 한다. `OrderHandler.onCancel`도 동일하다.

### 규칙 3. 결정적 ID — chap044 어디에 있나

모든 보상 명령이 `saga-{orderId}-{action}` 형태의 결정적 ID를 갖는다.
- `saga-42-increase` (재고 복구)
- `saga-42-cancel` (주문 취소)

랜덤 UUID가 아니다. 재발행돼도 같은 ID다.

### 규칙 4. 실패 이벤트도 퍼블리시 — chap044 어디에 있나

`ProductHandler.onDecrease`:
```java
boolean success = (product != null && product.getQuantity() >= cmd.getQuantity());
// ... 차감 로직 ...
kafkaTemplate.send("product-decreased-event", ...,
    new ProductDecreasedEvent(cmd.getOrderId(), cmd.getProductId(), success));
```

`success=false`여도 **이벤트를 발행**한다. 그래야 Orchestrator가 실패를 감지하고 보상 Saga를 시작한다.

`DeliveryHandler.onCreate`도 동일:
```java
boolean valid = (cmd.getAddress() != null && !cmd.getAddress().isBlank());
// ... 저장 로직 ...
kafkaTemplate.send("delivery-created-event", ...,
    new DeliveryCreatedEvent(cmd.getOrderId(), deliveryId, valid));
```

### 규칙 5. 지휘자의 순수성 — chap044 어디에 있나

`OrderOrchestrator`의 네 메서드를 보라. 어디에도:
- "재고를 직접 차감"하는 코드 없음
- "주소를 검증"하는 코드 없음
- "주문을 직접 삭제"하는 코드 없음

모두 **다른 서비스에 명령을 발행**하고, **다른 서비스가 발행한 이벤트에 반응**할 뿐이다. 지휘자는 지휘만 한다.

## 7.5 시뮬레이터로 확인하기

두 번째 탭에서 **"⚠ Fail Path"** 버튼을 누른다. 이것이 **시나리오 A**(재고 부족)다. 토픽 흐름은:
1. `order-created-event` 🟡
2. `product-decrease-command` 🟠
3. `product-decreased-event` 🟡 (**실패 상태**, 색깔 달라짐)
4. `order-cancel-command` 🟠

`product-decreased-event`의 점이 **빨간 테두리** 또는 **다른 표시**를 가질 수 있다. 그것이 "실패"의 시그널이다.

**"✖ Cancel Path"** 버튼은 **배송 후 취소** 시나리오를 시뮬레이션한다(chap044 구현과는 약간 다를 수 있으나, 시각화 목적).

**주목할 DB 패널의 변화**:
- `product_tb`의 quantity가 10 → 9 → 10으로 움직인다 (차감 후 복구).
- `order_tb`의 status가 PENDING → CANCELLED로 한 번만 움직인다.
- `processed_message_tb`에 2~4개의 행이 생긴다.

시뮬레이터를 반복해서 돌려보자. **각 토픽의 메시지가 어떤 순서로 발생**하는지, **각 DB 테이블이 언제 어떻게 변화**하는지, 직접 눈으로 익혀야 한다. 글로 읽는 것보다 3배 강력하다.

## 7.6 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 보상 트랜잭션은 **이미 커밋된 정방향 트랜잭션을 상쇄**하는 또 다른 트랜잭션이다.
- 보상은 정확한 롤백이 아니라 **반대 행위**이다. 이력이 남는다.
- Saga의 5가지 불변 규칙: 역순 보상, 보상 멱등, 결정적 ID, 실패 이벤트 퍼블리시, 지휘자의 순수성.
- chap044는 재고 부족(시나리오 A)과 배송 실패(시나리오 B) 두 가지 실패 경로를 구현한다.
- 시나리오 A는 보상이 불필요하다(차감 자체가 안 일어났으므로).
- 시나리오 B는 **재고 복구 + 주문 취소** 두 보상을 역순으로 발행한다.
- Orchestrator는 **명령만 발행하고 이벤트만 구독**한다. 비즈니스 로직은 절대 담지 않는다.

Saga 패턴과 보상까지 이해했으면, 이 책의 절반을 지난 것이다. 그러나 이 모든 것이 **한 가지 전제** 위에 서 있다. 그 전제가 무너지면 모든 것이 무너진다.

그 전제는 **"같은 명령이 두 번 와도 같은 결과가 되어야 한다"**는 것이다. 멱등성(Idempotency)이다. 다음 장에서 본다.

---

# 8장. 같은 전갈을 두 번 받으면

## 8.1 비유로 보기 — 두 번 온 편지

우체국 직원이 오늘 같은 편지를 **두 번** 배달해왔다. 편지 내용은 이렇다.

> "MacBook Pro 재고 1개를 차감하시오."

점원은 당황한다. 두 번 차감해야 할까? 아니면 한 번만? 편지 봉투를 보니 **일련번호 `saga-42-decrease`**가 찍혀 있다. 점원은 장부를 펼쳐서 그 번호를 찾아본다.

**첫 번째 편지를 받았을 때의 상황.**
- 장부에 `saga-42-decrease`가 없다.
- 점원은 편지대로 재고를 1개 차감한다 (10 → 9).
- 장부에 `saga-42-decrease` 번호를 기입한다.

**두 번째 편지를 받았을 때의 상황.**
- 장부에 `saga-42-decrease`가 이미 있다!
- 점원은 편지를 쓰레기통에 버린다.
- 재고는 그대로 9.

이것이 **멱등성(idempotency)**의 실체다. "같은 일련번호의 편지는 **한 번만 처리**한다." 그러면 같은 편지가 백 번 와도 재고는 정확히 1번만 차감된다.

이 비유에서 세 가지 핵심을 뽑아낼 수 있다.
1. **일련번호**가 모든 메시지에 있어야 한다 (결정적 messageId).
2. **장부**가 있어야 한다 (`processed_message_tb` 같은 중복 방지 테이블).
3. **"처리했는지 먼저 기록하고, 그 다음 실제 처리"**가 아니라 **"처리했는지 먼저 확인하고, 그 다음 실제 처리 + 기록"**이다. 순서가 미묘하게 중요하다 (뒤에 자세히).

## 8.2 개념으로 정의하기 — 멱등성

### 수학적 정의

수학에서 **멱등 함수(idempotent function)**란 `f(f(x)) = f(x)`인 함수다. 즉 **여러 번 적용해도 한 번 적용한 것과 같다**.

예:
- `|x|` (절댓값): `||x|| = |x|` ✅
- `max(x, 10)`: `max(max(x, 10), 10) = max(x, 10)` ✅
- `x + 1`: `(x + 1) + 1 ≠ x + 1` ❌ 멱등 아님

프로그래밍에서의 멱등성도 같은 개념이다. 같은 연산을 **여러 번 실행해도 결과가 같다**면 멱등적이다.

### HTTP의 멱등성

HTTP 메서드의 멱등성은 이미 표준에 박혀 있다.

| 메서드 | 멱등적? |
|---|---|
| GET | 예 (읽기만 함) |
| PUT | 예 (같은 값으로 덮어씀) |
| DELETE | 예 (이미 삭제된 건 다시 삭제해도 같음) |
| POST | **아니오** |

POST는 기본적으로 멱등적이지 않다. "주문 생성" POST를 두 번 보내면 주문이 두 개 생성될 수 있다.

그래서 **POST를 멱등적으로 만들려면** 서버 측에서 별도 처리가 필요하다. 일반적인 방법은:
- 클라이언트가 **요청 ID**(Idempotency Key)를 헤더에 넣어 보낸다.
- 서버는 그 ID가 이미 처리된 적이 있는지 확인한다.
- 이미 있으면 이전 결과를 그대로 돌려준다 (새로 만들지 않음).

Stripe API 같은 결제 시스템이 이 방식을 쓴다. chap044는 HTTP가 아니라 Kafka지만, **원리는 동일**하다.

### 왜 멱등성이 필수인가 — 중복이 생기는 세 가지 이유

Kafka at-least-once 환경에서 **중복은 피할 수 없다**. 중복이 생기는 세 가지 원인:

#### 원인 1. Producer의 재시도

Producer가 Kafka 브로커에 메시지를 보냈다. 브로커는 저장하고 ACK를 보냈다. 그런데 네트워크 지연으로 ACK가 Producer에 도착하지 않았다.

Producer: "응답이 없네. 재전송하자."
→ 브로커에 **같은 메시지가 두 번** 저장됨.

해결: Producer의 `enable.idempotence=true` 설정으로 Producer 측 중복을 막을 수 있지만(Exactly-once Producer), Consumer 측 중복은 여전히 발생 가능.

#### 원인 2. Consumer의 재시작과 오프셋

Consumer가 메시지 5번을 처리했다. DB에 재고 차감을 커밋했다. 그러나 **오프셋을 커밋하기 전에** 죽었다.

다시 시작된 Consumer: "나는 오프셋 4까지 처리했지? 5번부터 다시 읽어야지."
→ 5번 메시지를 **또 처리**한다. 재고가 **두 번 차감**된다.

이 문제를 해결하기 위한 두 가지 전략:
- **커밋을 처리 전에**: 메시지를 잃을 수 있다 (at-most-once).
- **커밋을 처리 후에**: 중복 처리될 수 있다 (at-least-once).

두 번째가 일반적이고, 그 비용이 **멱등성 부담**이다.

#### 원인 3. Consumer Group의 리밸런싱

Consumer 그룹에 새 멤버가 들어오거나 나가면 **리밸런싱(rebalance)**이 일어난다. 파티션이 재분배되는 과정에서, **처리 중이던 메시지**가 다른 컨슈머에게 다시 할당될 수 있다.

이 "처리 중이던" 메시지가 **두 컨슈머에게 중복 처리**되는 순간이 있을 수 있다.

해결: 멱등성을 통해 흡수.

### Exactly-once Semantics — 비싼 이상

"at-least-once의 중복이 귀찮으면, **Exactly-once를 쓰면 되지 않나?"**

Kafka는 Exactly-once Semantics(EOS)를 지원한다. 그러나:
- Producer, Broker, Consumer **모두 트랜잭셔널**이어야 함.
- 성능 오버헤드가 크다.
- 외부 시스템(DB)과의 통합은 **별도 설계**가 필요 (Read-Process-Write가 Kafka 내부에서만 완결될 때 EOS 보장).

chap044처럼 "Kafka에서 읽고 → MySQL에 쓰고 → Kafka로 또 쏘는" 복합 흐름에서는 EOS를 그대로 쓰기 어렵다. 그래서 실무의 대세는:

**At-least-once + 멱등성 + Outbox 패턴**

이 조합이 **실질적인 Exactly-once**를 달성한다. 우리는 이 길을 택한다.

### 멱등성의 세 가지 구현 전략

멱등성을 구현하는 전통적 방법 세 가지.

#### 전략 1. 자연스러운 멱등 연산

연산 자체가 멱등하도록 설계한다.

예:
- "X를 10으로 설정" (UPDATE SET x=10) — 멱등적
- "X를 1 증가" (UPDATE SET x=x+1) — 멱등적이지 **않음**

chap044의 "재고 차감"은 자연스럽게 멱등하지 **않다**. 따라서 다른 전략이 필요하다.

#### 전략 2. 버전/상태 기반 검사

메시지에 **상태 또는 버전**을 담고, 대상 엔티티의 현재 상태와 비교한다.

예: "주문 42를 PENDING에서 COMPLETED로 변경하라." 이미 COMPLETED라면 **무시**한다.

장점: 별도 테이블 불필요.
단점: 상태 전이를 모두 설계해야 함. 복잡.

#### 전략 3. 중복 메시지 테이블 (Dedupe Table) — chap044 방식

**메시지 ID를 기록하는 별도 테이블**을 두고, 같은 ID의 메시지는 처리를 건너뛴다.

장점: 단순하고 일반적. 모든 종류의 메시지에 적용 가능.
단점: 테이블 크기가 무한히 커질 수 있음(TTL 필요).

chap044는 **전략 3**을 쓴다. 그것이 가장 단순하고 교육적이기 때문이다.

## 8.3 chap044의 2중 방어선

chap044는 멱등성을 **두 층**에서 보장한다.

### 1차 방어선 — 결정적 messageId

Orchestrator 측의 책임이다. **같은 의도의 명령은 항상 같은 messageId를 갖도록** 한다.

```java
send("product-decrease-command", orderId,
    new ProductDecreaseCommand(
        "saga-" + orderId + "-decrease",  // ← 결정적
        orderId, event.getProductId(), event.getQuantity()));
```

"주문 42의 재고 차감 명령"이면 언제나 `saga-42-decrease`다. **재시도해도 같은 ID**. 이것이 수신자 측에서 중복을 감지할 수 있는 기반이다.

만약 이 ID가 `UUID.randomUUID()` 같은 랜덤 값이라면:
- 매번 다른 ID → 수신자는 **둘이 같은 명령인지 알 수 없음**.
- 중복 감지 불가능.

### 2차 방어선 — `processed_message_tb`

Consumer 측의 책임이다. **이미 처리한 messageId는 DB에 기록하고, 들어올 때마다 확인**한다.

### 💻 코드 스냅샷 ⑥ — `isDuplicate()`의 심장

`product/src/main/java/com/metacoding/product/handler/ProductHandler.java` (발췌)

```java
private boolean isDuplicate(String messageId) {
    try {
        processedMessageRepository.save(
            new ProcessedMessage(messageId, SERVICE_NAME));
        return false;  // 첫 처리
    } catch (DataIntegrityViolationException e) {
        log.info("🔁 중복 메시지 스킵: {}", messageId);
        return true;  // 중복
    }
}
```

이 열 줄이 **chap044의 모든 멱등성**을 지탱한다. 세 가지 교묘한 점이 있다.

**교묘한 점 1. 확인과 기록을 하나의 SQL로.**

일반적인 접근은 "먼저 SELECT로 존재 확인 → 없으면 INSERT"다. 그러나 이 방식은 **동시성 문제**가 있다. 두 스레드가 동시에 SELECT를 해서 "없다"고 판단하고, 둘 다 INSERT를 시도할 수 있다.

chap044는 더 똑똑하다. **INSERT를 먼저 시도**한다.
- 성공 → 첫 처리
- 실패(PK 중복) → 이미 처리된 것

이것이 원자적이고 경쟁 조건에 안전하다. **PK 제약조건 자체를 중복 감지 메커니즘**으로 쓴 것이다.

**교묘한 점 2. PK가 `message_id`.**

```sql
CREATE TABLE processed_message_tb (
  message_id VARCHAR(100) PRIMARY KEY,
  service_name VARCHAR(30),
  processed_at DATETIME
);
```

`message_id`가 PK이므로 중복 INSERT는 반드시 `DataIntegrityViolationException`을 발생시킨다. Spring Data JPA가 이 예외를 사용한다. 다른 ORM/DB에서도 해당 예외를 캐치하면 같은 패턴을 쓸 수 있다.

**교묘한 점 3. 같은 테이블을 여러 서비스가 공유하지만 `service_name`으로 구분.**

Product 서비스도, Order 서비스도, Delivery 서비스도 모두 같은 테이블을 쓴다. 그러나 `service_name` 컬럼에 `"product"`, `"order"`, `"delivery"`가 기록된다.

이는 디버깅과 감사를 위한 장치다. PK는 `message_id`라서 논리적으로 서비스 구분이 없어도 작동하지만, **누가 처리했는지** 로그로 남긴다.

### 💻 코드 스냅샷 ⑦ — 테이블 DDL

`db/init.sql` (발췌)

```sql
-- 멱등성 처리 테이블 (중복 메시지 방지)
-- 각 서비스(product/order/delivery)가 공용 DB에서 공유
-- messageId는 orchestrator가 결정적으로 생성: saga-{orderId}-{action}
CREATE TABLE processed_message_tb (
  message_id VARCHAR(100) PRIMARY KEY,
  service_name VARCHAR(30),
  processed_at DATETIME
);
```

단순하지만 **기능에 필요한 최소한**이다. 추후 프로덕션에서 확장할 것들:
- `processed_at`에 인덱스 (TTL 정리용)
- `message_id`의 도메인 접두사 분리 (`saga` prefix 등)
- 파티셔닝 (메시지 수가 수천만 건 넘어가면)

### 핸들러의 트랜잭션 경계

여기서 매우 미묘한 문제가 있다. `isDuplicate` 체크와 비즈니스 로직이 **같은 트랜잭션** 안에 들어가야 한다.

`ProductHandler.onDecrease`:
```java
@KafkaListener(topics = "product-decrease-command", groupId = "product-service")
@Transactional   // ← 이 한 줄이 중요!
public void onDecrease(ProductDecreaseCommand cmd) {
    if (isDuplicate(cmd.getMessageId())) return;
    // 재고 차감 로직
    // ...
    kafkaTemplate.send("product-decreased-event", ...);
}
```

`@Transactional`이 **왜** 필요한가? 다음 시나리오를 상상해보자.

**`@Transactional` 없을 때.**
1. `isDuplicate` 호출 → `processed_message_tb`에 INSERT 성공 → 즉시 커밋
2. 재고 차감 시도 → DB 에러 (예: 타임아웃) → 롤백
3. Consumer가 예외를 던짐 → Kafka가 오프셋 커밋 안 함 → 재시도
4. 재시도 시 `isDuplicate` 호출 → 이미 기록됨 → **중복으로 인식해서 처리 안 함**
5. 결과: 재고 차감이 **영원히 안 일어남**. 메시지가 **사일런트 실패**.

**`@Transactional` 있을 때.**
1. 트랜잭션 시작
2. `isDuplicate` 호출 → INSERT 성공 (아직 커밋 안 됨)
3. 재고 차감 시도 → DB 에러 → 예외 발생 → 트랜잭션 **롤백** → INSERT도 되돌려짐
4. 재시도 시 `isDuplicate` 호출 → 기록 없음 → 정상 처리

`@Transactional`이 있어야 **"실제 처리가 실패하면 기록도 취소"**라는 올바른 의미가 보장된다.

### 그러나 또 다른 미묘함 — Producer의 실패

만약 재고 차감은 성공했는데 **`kafkaTemplate.send("product-decreased-event", ...)`가 실패**하면?

- 트랜잭션은 이미 DB 커밋 직전이다.
- Kafka 실패 예외가 발생하면 트랜잭션이 롤백된다 (DB 커밋 안 됨, `processed_message_tb` 기록 안 됨).
- 재시도 시 처음부터 다시.

이 동작은 괜찮다. **"성공 이벤트를 못 보내면 차감도 취소"**라는 의미다.

하지만 **트랜잭션 커밋 후 Kafka 전송이 실패**하면? 예를 들어 DB 트랜잭션은 commit()됐는데 `kafkaTemplate.send()`가 그 후에 실패하면?

- DB 커밋은 되었다. `processed_message_tb`에도 기록되었다.
- 그러나 이벤트는 Kafka에 안 갔다.
- Orchestrator는 영원히 응답을 못 받는다.

이것이 **Outbox 패턴**이 필요한 이유다. Outbox 패턴은:
1. 비즈니스 로직과 함께 **outbox_tb에도 "보낼 메시지"를 INSERT**한다 (같은 트랜잭션).
2. 별도 프로세스가 outbox_tb를 **폴링**해서 Kafka로 전송한다.
3. 전송 성공 시 outbox_tb에서 삭제.

chap044는 Outbox 패턴을 쓰지 않는다. 교육 편의상 **가정을 축소**한 것이다. 13장에서 다시 다룬다.

## 8.4 멱등성이 없을 때 일어나는 악몽

멱등성을 뺀 시스템이 어떻게 무너지는지 보자.

### 악몽 시나리오 — 재고가 음수가 된다

#### 설정
- MacBook Pro 재고: 5개
- Orchestrator가 5번의 주문을 처리 중.
- Consumer 재시작 문제로 `product-decrease-command`가 **2번씩** 전달됨.

#### 멱등성 있음 (chap044 현재 상태)

| 주문 | 원본 메시지 | 중복 메시지 | 처리 결과 | 재고 |
|---|---|---|---|---|
| 42 | decrease(1) | decrease(1) | 원본만 처리 | 5→4 |
| 43 | decrease(1) | decrease(1) | 원본만 처리 | 4→3 |
| 44 | decrease(1) | decrease(1) | 원본만 처리 | 3→2 |
| 45 | decrease(1) | decrease(1) | 원본만 처리 | 2→1 |
| 46 | decrease(1) | decrease(1) | 원본만 처리 | 1→0 |

결과: 재고 0. **정상**.

#### 멱등성 없음

| 주문 | 원본 메시지 | 중복 메시지 | 처리 결과 | 재고 |
|---|---|---|---|---|
| 42 | decrease(1) | decrease(1) | 두 번 모두 처리 | 5→4→3 |
| 43 | decrease(1) | decrease(1) | 두 번 모두 처리 | 3→2→1 |
| 44 | decrease(1) | decrease(1) | 두 번 모두 처리 | 1→0→-1 (!) |
| 45 | decrease(1) | decrease(1) | 두 번 모두 처리 | -1→-2→-3 |
| 46 | decrease(1) | decrease(1) | 두 번 모두 처리 | -3→-4→-5 |

결과: 재고 -5. **재고가 음수**. 10개 주문된 것. **5개 고객의 상품이 실물 재고 없음**.

이 악몽은 실제 이커머스에서 반복적으로 일어났고, 이로 인한 고객 환불과 신뢰 추락은 수천만 원에서 수억 원 규모의 손실을 기록했다. 멱등성은 **보험**이 아니라 **필수 설계**다.

### 악몽 시나리오 2 — 주문이 두 번 취소된다

주문 42가 CANCELLED로 변경되었다. 그런데 `order-cancel-command`가 중복 도착했다.

멱등성 없이 그냥 처리하면:
- 이미 CANCELLED인 상태에서 **또 CANCELLED로 업데이트** → SQL 입장에서는 성공 (값이 같음).
- 그러나 **웹소켓 푸시는 두 번** 나간다.
- 사용자는 "주문이 취소되었습니다"라는 알림을 **두 번** 받는다.
- 사용자 경험이 나빠지고, 경우에 따라 고객 서비스 문의가 쏟아진다.

멱등성 있으면:
- `isDuplicate("saga-42-cancel")` 체크에서 중복 감지.
- 두 번째 처리는 조용히 건너뜀.
- 사용자는 **알림을 한 번만** 받는다.

## 8.5 멱등성 테이블의 성능과 관리

### 테이블 크기의 무한 증가

`processed_message_tb`는 메시지가 처리될 때마다 한 행씩 쌓인다. 시간이 지나면 **수천만 건**이 쌓인다. 두 가지 문제가 생긴다.

**문제 1. 디스크 공간.** 천만 건이면 수 GB가 된다. 별것 아니지만 꾸준히 늘어난다.

**문제 2. INSERT 성능.** PK 인덱스가 커지면 INSERT가 느려진다. B-Tree가 깊어지기 때문.

### 해결 — TTL 기반 청소

**TTL(Time-To-Live)** 정책을 둔다. `processed_at`이 일정 기간(예: 30일) 이상 지난 행은 삭제한다.

왜 30일인가? Kafka의 메시지 보관 기간과 맞추면 된다. Kafka 기본값은 7일인데 보안상 30일로 늘려두는 경우가 많다. 그보다 오래된 메시지는 어차피 Kafka에도 없으므로 재처리될 가능성이 0이다.

```sql
-- 매일 한 번 실행할 청소 쿼리
DELETE FROM processed_message_tb
WHERE processed_at < NOW() - INTERVAL 30 DAY;
```

이를 위해 **`processed_at` 컬럼에 인덱스**를 추가하는 것이 좋다.

```sql
CREATE INDEX idx_processed_at ON processed_message_tb (processed_at);
```

### 대안 — 파티셔닝

테이블이 억 단위가 되면 TTL DELETE도 느려진다. 대신 **테이블 파티셔닝**(월별 또는 주별)으로 관리하면:
- 오래된 파티션을 **통째로 drop** 가능 (INSTANT 작업).
- INSERT/SELECT가 현재 파티션에만 영향.

chap044는 이 단계까지는 필요 없다. 교육용 데이터는 수백 건에 그친다.

### Redis로 대체 — 또 다른 옵션

MySQL 대신 **Redis**를 쓸 수도 있다.

```
Redis: SET processed:saga-42-decrease 1 EX 2592000
       → 30일 TTL 자동. 오래된 키는 Redis가 자동 삭제.
```

장점: 속도가 빠르고 TTL 관리 자동.
단점: Redis가 **별도 서비스**라 운영 부담 추가. Redis 장애 시 중복 제거 실패.

chap044는 RDBMS 방식을 택했다. 그것이 "추가 인프라 없이 설명 가능"하기 때문이다.

## 8.6 시뮬레이터로 확인하기

두 번째 탭의 DB 패널에서 **`processed_message_tb`**를 주목하자.

"▶ Happy Path"를 한 번 돌리면 **3개의 행**이 추가된다.
- `saga-42-decrease` (product 서비스)
- `saga-42-create-delivery` (delivery 서비스)
- `saga-42-complete` (order 서비스)

이 세 개가 chap044에서 멱등 처리되는 **모든 Command**에 해당한다.

**"⚠ Fail Path"**를 돌리면 더 재밌다. 재고 부족 시나리오에서는:
- `saga-42-decrease` (product)
- `saga-42-cancel` (order)

두 개의 행이 추가된다. 멱등성 테이블을 보면 **어떤 Saga가 어떤 명령까지 실행했는지** 한눈에 보인다.

시뮬레이터의 재밌는 실험: **같은 버튼을 두 번 빠르게 누르기**. 시뮬레이터가 중복을 어떻게 처리하는지 관찰하자. (구현에 따라 다르지만, 잘 된 시뮬레이터라면 두 번째 클릭은 중복 스킵을 표시한다.)

## 8.7 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 멱등성: 같은 연산을 여러 번 해도 결과가 같다.
- Kafka의 at-least-once는 중복을 불가피하게 만든다. Producer 재시도, Consumer 재시작, 리밸런싱 모두 원인.
- Exactly-once Semantics는 이상적이지만 비용이 크고, 외부 DB 통합에는 한계가 있다.
- chap044는 **결정적 messageId + dedupe 테이블**로 멱등성을 구현한다.
- `isDuplicate()`는 **INSERT 먼저 → 실패 시 중복**이라는 원자적 패턴을 쓴다.
- `@Transactional`로 DB 기록과 비즈니스 로직을 한 트랜잭션에 묶어야 한다.
- 멱등성 없으면 재고가 음수가 되고 알림이 중복 발송되는 재앙이 일어난다.
- 멱등성 테이블은 **TTL 또는 파티셔닝**으로 관리한다. 그렇지 않으면 무한 성장.

멱등성은 **명령에 꼭 필요**하다고 했다. 그런데 이상하지 않은가? 이벤트도 중복되지 않는가? chap044는 왜 이벤트에는 멱등성 처리를 하지 않는가? 이 질문의 답이 다음 장이다.

---

# 9장. 명령과 이벤트, 왜 멱등 처리가 다른가

## 9.1 질문을 정확히 다시 하기

8장에서 chap044가 `processed_message_tb`에 기록하는 메시지 ID를 보자.

- `saga-42-decrease` ← Command
- `saga-42-create-delivery` ← Command
- `saga-42-complete` ← Command
- `saga-42-cancel` ← Command
- `saga-42-increase` ← Command

**5개 모두 Command**다. Event는 하나도 없다.

그런데 Orchestrator는 `order-created-event`, `product-decreased-event`, `delivery-created-event`, `delivery-completed-event` **4개의 이벤트를 구독**한다. 이 이벤트들도 중복 도착할 수 있다. 그러면 Orchestrator는 중복을 어떻게 감당하나?

답은 "**자연스럽게 흡수된다**"이다. 왜 그런지 설명한다.

## 9.2 이벤트 중복이 자연스럽게 흡수되는 이유

### 시나리오: `product-decreased-event`가 두 번 온 경우

**첫 번째 수신:**
- Orchestrator: `states.get(42)` → WorkflowState 존재
- `event.isSuccess()` == true
- → `delivery-create-command` 발행
  - 메시지 ID: `saga-42-create-delivery`

**두 번째 수신:**
- Orchestrator: `states.get(42)` → WorkflowState **아직 존재** (삭제 안 됨)
- `event.isSuccess()` == true
- → `delivery-create-command` **또 발행** ← 중복!
  - 메시지 ID: 같음 (`saga-42-create-delivery`) ← **여기가 핵심!**

**Delivery 서비스가 같은 `saga-42-create-delivery`를 두 번 받는다.**
- 첫 번째: `isDuplicate` 통과, 배송 생성, 이벤트 발행.
- 두 번째: `isDuplicate`에서 **중복 감지**, 스킵.

결과: **배송이 두 번 생성되지 않음**. 이벤트 중복이 Command 멱등성을 통해 자연스럽게 흡수되었다.

### 더 흥미로운 시나리오: 성공 이벤트 뒤에 취소 이벤트

**시나리오 B에서 배송 생성 실패 → 재고 복구 + 주문 취소 명령이 이미 발행되어 Saga 상태도 제거된 직후**에, **`product-decreased-event(success)`가 뒤늦게 한 번 더** 온다고 하자.

- Orchestrator: `states.get(42)` → `null` (이미 `states.remove(42)` 되었음)
- 조기 return

```java
WorkflowState state = states.get(orderId);
if (state == null) return;  // ← 여기서 걸러짐
```

**state.remove()가 보호막** 역할을 한다. Saga가 종료된 주문에 대한 뒤늦은 이벤트는 조용히 무시된다.

이 매커니즘은 멱등적이지는 않지만 **결과적으로 같은 효과**를 낸다. 이미 완료된 Saga에 대한 중복 이벤트는 **no-op**(아무 일도 하지 않음)이기 때문이다.

## 9.3 구조적 이유 — Event는 "결과", Command는 "의도"

이 이야기의 본질은 철학에 있다.

### Event는 "결과"다

"재고가 차감되었다"는 **이미 일어난 사실**을 방송하는 것이다. 이미 일어난 사실은 **두 번 일어나지 않는다**. 한 번 일어났고, 그것을 여러 번 알린다고 해서 사실 자체가 두 번이 되는 것은 아니다.

이벤트를 받는 쪽은 **정보를 업데이트**하거나 **다음 단계를 트리거**한다. 같은 정보 업데이트를 여러 번 해도 상태는 같다 (현재 상태로 덮어쓰기).

따라서 이벤트는 **자연스럽게 멱등적 반응**을 유발한다. 별도의 중복 처리가 없어도 문제가 생기지 않는다.

### Command는 "의도"다

"재고를 차감하라"는 **지시**다. 이 지시는 같은 내용이라도 **새로운 작업**으로 해석될 수 있다. 두 번 받으면 두 번 차감해버릴 수 있다. 이것이 Command에 명시적 멱등성 처리가 필요한 이유다.

### 비유로 다시 보기

이벤트의 예:
- 경보음: "불이 났다!"라는 경보가 여러 번 울려도 불은 한 번만 난 것이다. 반응은 "소방서 부르기"로 동일. 한 번 불러도, 이미 부른 상황에서 또 부르면 번호만 중복이지 결과는 같음.

명령의 예:
- 지시: "소화기 한 병 써라!" 이 지시가 두 번 오면 소화기를 두 병 쓴다. 낭비가 생긴다. 그래서 "지시 번호"를 붙여서 같은 번호의 지시는 한 번만 수행.

## 9.4 예외 케이스 — 이벤트에도 멱등 처리가 필요한 경우

항상 그런 건 아니다. 다음 경우에는 **이벤트도 멱등 처리**해야 한다.

### 경우 1. 이벤트가 외부 부작용을 만드는 경우

"주문 완료되었다"는 이벤트를 받고 **이메일을 보내는** 서비스를 생각해보자. 같은 이벤트를 두 번 받으면 이메일을 **두 번 보낸다**. 사용자는 중복 이메일을 받는다.

이 경우 이메일 서비스도 `processed_message_tb` 같은 테이블로 이벤트 ID를 추적해야 한다.

chap044는 이런 외부 부작용 서비스가 없어서 문제가 안 된다. 그러나 프로덕션에서 이메일·SMS·외부 API 호출 같은 부작용이 있는 이벤트 처리는 **반드시** 멱등화해야 한다.

### 경우 2. 이벤트가 집계·카운팅을 유발하는 경우

"주문이 생성되었다" 이벤트를 받고 **통계 서비스가 주문 수를 증가**시킨다고 하자. 같은 이벤트를 두 번 받으면 카운트가 **두 번 증가**한다. 통계가 틀어진다.

이 경우에도 `processed_message_tb`가 필요하다.

### 경우 3. 이벤트가 CQRS의 Write side인 경우

CQRS에서 이벤트를 받아 **Read Model을 업데이트**하는 경우, 중복 이벤트는 **같은 업데이트**를 두 번 하게 된다. 대부분은 문제없지만, 이벤트 기반 머티리얼라이즈드 뷰는 순서 민감도가 있어 조심해야 한다.

## 9.5 chap044의 원칙 정리

chap044는 다음 원칙을 따른다.

1. **Command는 모두 결정적 messageId를 갖는다.**
   - Orchestrator가 `saga-{orderId}-{action}` 형식으로 생성.
2. **Command 수신자는 모두 `isDuplicate()` 체크를 첫 줄에 둔다.**
   - Product, Order, Delivery 세 서비스 모두.
3. **Event 수신자는 별도 멱등성 체크를 하지 않는다.**
   - Orchestrator의 이벤트 핸들러는 `states.get()` null 체크로 자연스럽게 흡수.
4. **Event 발행자는 성공/실패 모두 이벤트를 발행한다.**
   - `success` 플래그로 결과를 담아 발행.

이 원칙의 장점은:
- 코드가 단순해진다 (불필요한 테이블 조회 줄어듦).
- 의도가 명확해진다 (누가 무엇을 책임지는지 이름만 봐도 앎).
- 성능이 좋아진다 (이벤트 처리마다 DB 조회 생략).

단점은:
- 이벤트 처리에 외부 부작용이 추가되면 **원칙을 재검토**해야 한다.
- 동시성 환경에서 `states` Map 접근의 일관성에 의존 (ConcurrentHashMap 사용).

## 9.6 Command 5종 vs Event 4종 한눈에 보기

chap044의 모든 메시지를 처리 방식과 함께 정리하면 이렇다.

| 이름 | 종류 | `processed_message_tb` 기록? | 이유 |
|---|---|---|---|
| `order-created-event` | Event | ❌ | states 초기 진입. 중복 시 같은 WorkflowState 덮어쓰기 → 무해 |
| `product-decrease-command` | Command | ✅ | 재고 차감은 자연 멱등 아님 |
| `product-decreased-event` | Event | ❌ | 결과 전달. 중복 시 `states.get`이 덮어쓰거나 null이 됨 → 무해 |
| `product-increase-command` | Command | ✅ | 재고 복구는 자연 멱등 아님 |
| `delivery-create-command` | Command | ✅ | 배송 INSERT는 자연 멱등 아님 |
| `delivery-created-event` | Event | ❌ | 결과 전달. states 체크로 자연 흡수 |
| `delivery-completed-event` | Event | ❌ | 결과 전달. 주문 완료 명령 자체가 멱등 |
| `order-complete-command` | Command | ✅ | 주문 상태 변경 + 웹소켓. 웹소켓 중복 방지를 위해 |
| `order-cancel-command` | Command | ✅ | 동일 이유 |

Command가 모두 체크하고 Event는 하나도 체크하지 않는다. 이 일관성이 chap044의 우아함이다.

## 9.7 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 이벤트는 **결과**이고 명령은 **의도**다. 성격이 다르다.
- 이벤트 중복은 "같은 결과에 반응하는 것"이라 대체로 **자연 흡수**된다.
- 명령 중복은 "같은 지시를 두 번 실행"하므로 명시적 차단이 필요하다.
- chap044는 Command에만 `processed_message_tb` 체크를 적용한다.
- 단, 이벤트에 외부 부작용·집계·Read Model 쓰기가 있으면 이벤트도 멱등 처리해야 한다.

기초 원리는 이쯤이면 모두 다뤘다. 이제 사용자가 이 모든 복잡함의 **결과만** 우아하게 받을 수 있도록, 작은 새 한 마리를 사용자의 어깨에 올려 줄 차례다. 다음 장의 주제다.

---

# 10장. 고객의 어깨 위의 작은 새

## 10.1 비유로 보기 — 작은 새

사용자가 주문하기 버튼을 누르고, 즉시 "주문 접수됨" 응답을 받았다. 그런데 주문이 실제로 완료될지, 아니면 취소될지는 아직 모른다. 비동기이기 때문이다.

전통적인 방법은 **새로고침**이다. 사용자가 5초마다 페이지를 새로고침하면 서버를 조회해서 상태를 알 수 있다. 그러나 이것은:
- 사용자에게 피곤한 경험
- 서버에는 **불필요한 트래픽 폭주**

현대적인 방법은 **작은 새**다. 사용자의 어깨에 작은 새 한 마리가 앉아 있다. 이 새는 **서버와 계속 연결된 줄**을 물고 있다. 서버에서 어떤 소식이 생기면 줄을 따라 즉시 새에게 전달되고, 새는 사용자에게 "소식이 왔어요!" 하고 알려준다.

이것이 **웹소켓(WebSocket)**이다. 사용자가 새로고침할 필요가 없다. 서버가 **능동적으로 밀어서** 알려준다.

## 10.2 개념으로 정의하기 — 웹소켓

### HTTP vs WebSocket

**HTTP**는 요청-응답 쌍이다. 클라이언트가 "달라"라고 해야 서버가 "여기"라고 준다. 서버가 먼저 뭔가 말할 수는 없다.

**WebSocket**은 양방향 지속 연결이다. 한 번 연결하면 **그 연결 위로 서버와 클라이언트가 자유롭게 메시지를 주고받을 수 있다**. TCP의 양방향성을 그대로 쓰는 것이다.

연결 수립 과정:
1. 클라이언트가 HTTP 요청 "이걸 웹소켓으로 업그레이드 해줘"를 보냄 (`Upgrade: websocket` 헤더).
2. 서버가 "OK, 업그레이드할게"라고 응답.
3. 그 순간부터 연결은 HTTP가 아닌 WebSocket 프로토콜로 전환.

한 번 수립되면 연결은 **오래 유지**된다. 분 단위, 시간 단위도 가능.

### 폴링과 웹소켓의 비교

| 방식 | 기법 | 단점 |
|---|---|---|
| **Short Polling** | 5초마다 조회 | 트래픽 폭주, 반응 지연 |
| **Long Polling** | 요청을 오래 기다리다 응답 | 여전히 요청 기반, 복잡 |
| **SSE** (Server-Sent Events) | 서버→클라 단방향 푸시 | 단방향 한계 |
| **WebSocket** | 양방향 지속 연결 | 방화벽/프록시 호환성 주의 |

chap044는 WebSocket을 쓴다.

### STOMP — 웹소켓 위의 메시징 프로토콜

순수 WebSocket은 **"연결은 만들어주지만 메시지 포맷은 알아서"**인 저수준 프로토콜이다. 애플리케이션 레벨에서 구조가 필요하다.

**STOMP(Simple Text Oriented Messaging Protocol)**는 웹소켓 위에 얹는 메시징 프로토콜이다. STOMP의 기본 명령:
- `CONNECT`: 연결
- `SUBSCRIBE`: 특정 "주제(topic)" 구독
- `SEND`: 메시지 전송
- `MESSAGE`: 구독한 주제로 들어오는 메시지

STOMP는 Kafka의 토픽 개념과 유사하게 **Publish-Subscribe 모델**을 제공한다. 클라이언트는 `/topic/orders/1`을 구독하고, 서버는 그 주제로 메시지를 발행한다.

### SockJS — 폴백 메커니즘

구형 브라우저나 일부 프록시는 WebSocket을 지원하지 않는다. **SockJS**는 이런 경우를 대비한 **폴백 라이브러리**다.

- 최신 브라우저: WebSocket 그대로 사용.
- 구형 또는 제약 환경: Long Polling, XHR 등으로 자동 전환.

클라이언트 코드는 같지만 내부적으로 적절한 전송 방식을 고른다. chap044는 SockJS를 쓴다.

## 10.3 chap044에선 이렇게 한다

### 연결의 구조

```
브라우저                              Order 서비스
  │                                       │
  │─── HTTP Upgrade ───────────────────► /ws
  │◄── 101 Switching Protocols ─────────│
  │                                       │
  │─── STOMP CONNECT ──────────────────►│
  │◄── CONNECTED ───────────────────────│
  │                                       │
  │─── STOMP SUBSCRIBE /topic/orders/1 ─►│
  │                                       │
  │ ... 연결 유지 ...                      │
  │                                       │
  │◄── MESSAGE /topic/orders/1 ─────────│ (주문 완료 시 서버가 푸시)
```

### Order 서비스의 WebSocket 설정

`WebSocketConfig.java`에 이렇게 쓰여 있다.

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

이 설정의 의미는:
- **`/ws`** 엔드포인트에서 STOMP 연결을 받는다.
- **`/topic/*`** 경로로 오는 메시지는 내장 브로커가 처리 (외부 브로커 안 씀).
- **SockJS 폴백** 활성화.

### 💻 코드 스냅샷 ⑧ — 서버에서 사용자에게 푸시

`order/src/main/java/com/metacoding/order/handler/OrderHandler.java` (발췌)

```java
private void pushToUser(Order order, String message) {
    messagingTemplate.convertAndSend(
        "/topic/orders/" + order.getUserId(),
        Map.of(
            "orderId", order.getId(),
            "status", order.getStatus(),
            "message", message
        )
    );
}
```

`messagingTemplate.convertAndSend(destination, payload)`가 핵심이다.
- `destination`: 메시지를 받을 STOMP 주제. 여기서는 **사용자별 개인 채널**.
- `payload`: 자동으로 JSON 직렬화된다.

내장 브로커가 이 주제를 **구독 중인** 클라이언트들에게 메시지를 즉시 전달한다.

### 왜 "/topic/orders/{userId}"인가

사용자별로 주제를 구분하는 이유는 **다른 사용자의 알림을 받지 않기 위해서**다.

사용자 1번의 주문 완료 알림을 사용자 2번이 받으면 개인정보 누출이자 UX 혼란이다. 그래서:
- 사용자 1: `/topic/orders/1` 구독
- 사용자 2: `/topic/orders/2` 구독

서버는 푸시할 때 `/topic/orders/{userId}`를 정확히 지정해서 해당 사용자만 받도록 한다.

**보안 주의**: 현재 chap044 설정은 클라이언트가 **아무 주제나 구독**할 수 있는 상태다. 악의적 클라이언트가 `/topic/orders/99`를 구독하면 99번 사용자의 알림을 볼 수 있다. 프로덕션에서는 `WebSocketMessageBrokerConfigurer`의 인터셉터로 **구독 주제에 JWT 검증**을 걸어야 한다. 교육 편의로 생략된 부분이다.

### 프론트엔드의 구독

`frontend/index.html` (발췌)

```javascript
function connectWebSocket() {
    stomp = Stomp.over(new SockJS('/ws'));
    stomp.connect({}, () => {
        stomp.subscribe('/topic/orders/' + userId, msg => {
            const data = JSON.parse(msg.body);
            if (data.status === 'COMPLETED')
                log('🎉 ' + data.message);
            else if (data.status === 'CANCELLED')
                log('💥 ' + data.message);
        });
    });
}
```

단순하다. SockJS로 `/ws`에 접속 → STOMP로 `/topic/orders/{userId}` 구독 → 메시지 오면 화면에 표시.

`userId`는 어디서 오는가? 3장에서 설명했듯이 **JWT 페이로드를 디코딩**해서 얻는다.

### 연결 복구

사용자가 네트워크 끊김을 경험하거나 서버가 재시작되면 웹소켓 연결이 끊긴다. 프로덕션 클라이언트는 다음을 구현해야 한다.

1. **연결 끊김 감지**: `stomp.onDisconnect` 또는 주기적 ping.
2. **재연결 시도**: 끊기면 1초, 2초, 4초… 백오프로 재연결.
3. **미수신 메시지 조회**: 재연결 후 "내가 놓친 메시지가 있나?"를 API로 재조회.

chap044는 이 복구 로직을 구현하지 않는다. 사용자가 페이지를 새로고침하면 다시 연결된다는 전제다. 프로덕션에서는 반드시 구현해야 한다.

## 10.4 Kafka와 WebSocket의 다른 점

둘 다 "메시지를 전달한다"는 점에서 비슷해 보인다. 그러나 구조와 목적이 다르다.

| 항목 | Kafka | WebSocket |
|---|---|---|
| 참여자 | 백엔드 서비스들 | 서버 ↔ 브라우저 |
| 방향 | 주로 서비스 간 비동기 통신 | 주로 서버 → 사용자 실시간 통지 |
| 지속성 | 메시지 영구 저장 (retention 기간 동안) | 연결 유지 시에만 도달 (연결 끊기면 소실) |
| 재생 | 오프셋 리셋으로 재생 가능 | 재생 불가 |
| 확장 | 파티션·Consumer Group | 세션 수만큼 TCP 연결 |

chap044에서 두 메시징의 역할 분담은 이렇다.

```
  서비스 간 대화           →  Kafka  (신뢰성·지속성 필요)
  사용자에게 실시간 통지    →  WebSocket (즉시성·양방향 필요)
```

Order 서비스가 Kafka로 주문 완료 명령을 받고, 그 결과를 사용자에게 WebSocket으로 푸시하는 것이 두 기술의 이어붙임이다.

## 10.5 시뮬레이터로 확인하기

두 번째 탭 "🛒 MSA 비즈니스 흐름"의 **브라우저 모형**을 보자.

로그인하면 화면 오른쪽 위에 **🟢 연결됨** 표시가 뜬다. 이것이 **웹소켓 연결 상태**다.

"▶ Happy Path"를 돌려서 끝까지 진행하면 브라우저 모형에 **🎉 주문이 완료되었습니다**가 녹색으로 떠오른다. 이것이 Order 서비스가 보낸 웹소켓 메시지다.

"⚠ Fail Path"에서는 **💥 재고 부족으로 주문이 취소되었습니다**가 빨간색으로 나타난다.

**실험**: 시뮬레이터에서 웹소켓 연결을 끊는 시나리오가 있다면 눌러보자. 끊긴 상태에서 주문하면 나중에 연결되어도 **놓친 알림은 받지 못한다**. 이것이 위에서 말한 "재연결 시 재조회 로직이 필요한" 이유다.

## 10.6 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- HTTP는 요청-응답이라 서버가 능동적으로 클라이언트에 말할 수 없다.
- WebSocket은 양방향 지속 연결이라 서버가 언제든 클라이언트에 푸시할 수 있다.
- STOMP는 웹소켓 위의 메시징 프로토콜로 Publish-Subscribe를 제공한다.
- SockJS는 WebSocket이 안 될 때의 폴백 라이브러리다.
- chap044는 Order 서비스가 `/ws` 엔드포인트에서 STOMP를 받고, `/topic/orders/{userId}`로 사용자별 푸시를 한다.
- 프로덕션에서는 연결 복구·구독 권한 검증·미수신 메시지 재조회가 필수다.
- Kafka(서비스 간)와 WebSocket(서버↔브라우저)는 서로 보완 관계다.

이 장까지 이 책의 **각 구성 요소**를 모두 다뤘다. 이제 이 모든 조각이 **한 장면**으로 어떻게 움직이는지, 다음 장에서 통째로 그려본다. 이 책에서 가장 큰 지도가 펼쳐진다.

---

# 11장. 전체를 한 장면으로

## 11.1 이 장의 목적

앞의 열 장에서 우리는 **아홉 개의 개념**을 따로따로 다뤘다.

| 장 | 개념 |
|---|---|
| 1 | 마이크로서비스 (네 점포) |
| 2 | 쿠버네티스 (관제탑과 터널) |
| 3 | JWT (손도장) |
| 4 | Kafka 기본 (방송실) |
| 5 | Command/Event 토픽 설계 (명령장과 소식지) |
| 6 | Saga 오케스트레이션 (지휘자) |
| 7 | Saga 보상 (지휘가 무너질 때) |
| 8 | 멱등성 (두 번 온 편지) |
| 9 | 명령 vs 이벤트 멱등 차이 |
| 10 | 웹소켓 (작은 새) |

이제 이 아홉 개가 **하나의 주문 흐름**에서 어떻게 동시에 작동하는지, **한 장에 엮어서** 보자. 이 장은 앞의 모든 장의 내용을 호출한다. 읽다가 이해가 안 되는 부분이 있으면 해당 장으로 돌아가자.

## 11.2 정상 시나리오 End-to-End (18단계)

### 설정

- 사용자: **ssar** (username), userId=1, password=1234
- 주문 상품: **MacBook Pro** (productId=1), 단가 2,500,000원, 재고 10개
- 주문 수량: **1개**
- 배송지: **"Seoul"**
- 주문이 생성되면 orderId=42번이 될 것
- 모든 메시지는 **정확히 한 번** 전달된다고 가정 (중복 없음)

### 전체 시퀀스 다이어그램

```
사용자  Browser  Ingress  Gateway  User  Order  Product  Delivery  Orch  Kafka
  │       │        │        │      │      │      │         │       │      │
  │ 입력   │        │        │      │      │      │         │       │      │
  │══════►│        │        │      │      │      │         │       │      │
  │        │─로그인─►        │      │      │      │         │       │      │
  │        │        │────────│─────►│      │      │         │       │      │
  │        │        │        │      │DB조회 │      │         │       │      │
  │        │        │        │      │JWT발급│      │         │       │      │
  │        │        │◄───────│◄─────│      │      │         │       │      │
  │        │◄───────│        │      │      │      │         │       │      │
  │◄ JWT 수신       │        │      │      │      │         │       │      │
  │                 │        │      │      │      │         │       │      │
  │ WS /ws 연결     │        │      │      │      │         │       │      │
  │━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━►│ (Order에 STOMP 연결)   │       │      │
  │◄━━━ subscribe /topic/orders/1 ━━━━━━━━━━━━━━━━━━━━━━━━━│       │      │
  │                 │        │      │      │      │         │       │      │
  │ 주문 클릭        │        │      │      │      │         │       │      │
  │──── POST /api/orders + Bearer ─────────►│      │         │       │      │
  │        │        │        │      │      │JWT검증│        │       │      │
  │        │        │        │      │      │insert │        │       │      │
  │        │        │        │      │      │delivery준비    │       │      │
  │        │        │        │      │      │─────── publish order-created ─►│
  │        │        │        │      │      │◄─ 200 OK ──────│       │      │
  │        │        │        │      │      │      │         │       │      │
  │◄──── "주문접수됨" ────────────────┤       │      │         │       │      │
  │                                   │      │      │         │       │      │
  │                                   │      │      │         │       │      │
  │ *** 이후는 백그라운드 ***          │      │      │         │       │      │
  │                                   │      │      │         │       │      │
  │                                   │      │      │         │◄consume order-created
  │                                   │      │      │         │states.put(42)
  │                                   │      │      │         │─publish product-decrease─►│
  │                                   │      │      │         │       │      │
  │                                   │      │◄consume product-decrease─────│
  │                                   │      │ isDup ck OK    │       │      │
  │                                   │      │ 10→9 차감      │       │      │
  │                                   │      │─publish product-decreased─►│
  │                                   │      │      │         │       │      │
  │                                   │      │      │         │◄consume product-decreased
  │                                   │      │      │         │ success=true
  │                                   │      │      │         │─publish delivery-create─►│
  │                                   │      │      │         │       │      │
  │                                   │      │      │◄consume delivery-create
  │                                   │      │      │ isDup ck OK    │      │
  │                                   │      │      │ insert delivery │      │
  │                                   │      │      │─publish delivery-created─►│
  │                                   │      │      │         │       │      │
  │                                   │      │      │         │◄consume delivery-created
  │                                   │      │      │         │ 배송완료 API 대기
  │                                   │      │      │         │       │      │
  │ ... 관리자가 PUT /api/deliveries/17/complete 호출 ...     │       │      │
  │                                   │      │      │ UPDATE status=COMPLETED │
  │                                   │      │      │─publish delivery-completed─►│
  │                                   │      │      │         │       │      │
  │                                   │      │      │         │◄consume delivery-completed
  │                                   │      │      │         │─publish order-complete─►│
  │                                   │      │      │         │       │      │
  │                                   │      │◄consume order-complete
  │                                   │      │ isDup ck OK    │       │      │
  │                                   │      │ UPDATE status=COMPLETED │      │
  │                                   │      │─── WS send /topic/orders/1 ──┤
  │◄━━━ MESSAGE: "주문이 완료되었습니다." ━━━━━━━━━━━━━━━━━━━━┤       │      │
  │                                   │      │      │         │       │      │
```

### 18개 단계 상세 설명

각 단계에서 어느 장의 지식이 쓰이는지 함께 표시한다.

#### 단계 1. 사용자가 로그인 폼에 `ssar / 1234` 입력 후 제출 [3장]

- 브라우저: `POST /api/login`
- 요청 본문: `{"username":"ssar","password":"1234"}`

#### 단계 2. Ingress → Gateway → User 서비스로 라우팅 [2장]

- Ingress가 도메인으로 들어온 HTTP를 받고
- Gateway(Nginx)가 `/api/login` 경로를 User 서비스로 전달
- User 서비스의 `UserController.login()` 호출

#### 단계 3. User 서비스가 `user_tb` 조회 + JWT 발급 [3장]

- `SELECT * FROM user_tb WHERE username = 'ssar' AND password = '1234'`
- 일치하면 `JwtProvider.create(1, "ssar")` 호출
- 응답: `{"token":"eyJ...","userId":1,"username":"ssar"}`

#### 단계 4. 브라우저가 토큰을 localStorage에 저장 [3장]

- JavaScript: `localStorage.setItem("token", data.token)`
- 이후의 모든 요청에 `Authorization: Bearer <token>` 헤더 첨부

#### 단계 5. 브라우저가 웹소켓 연결 수립 [10장]

- SockJS → `/ws` → STOMP CONNECT
- `STOMP SUBSCRIBE /topic/orders/1`
- 내부: Gateway가 `/ws` 요청을 Order 서비스로 전달, STOMP 세션 수립

#### 단계 6. 사용자가 상품을 선택하고 "주문하기" 클릭 [1장]

- MacBook Pro 선택, 수량 1, 주소 "Seoul"
- `POST /api/orders` (Authorization 헤더 포함)
- 본문: `{"productId":1,"quantity":1,"address":"Seoul"}`

#### 단계 7. Gateway → Order 서비스 → JWT 검증 → 주문 삽입 [3장]

- `OrderController`가 요청 수신
- `JwtVerifier`가 토큰 검증 → userId=1 추출
- `INSERT INTO order_tb (user_id, product_id, quantity, status) VALUES (1, 1, 1, 'PENDING')`
- 주문 ID = 42

#### 단계 8. Order가 `order-created-event` 발행 [4장, 5장]

- `kafkaTemplate.send("order-created-event", "42", payload)`
- 페이로드: `{orderId:42, userId:1, productId:1, quantity:1, address:"Seoul"}`
- 카프카 토픽 이름이 **과거형**(created)이다. Event임을 이름이 증명.

#### 단계 9. 사용자에게 HTTP 200 즉시 응답 [4장]

- Order 서비스는 Kafka에 쐈으므로 더 할 일 없음. 응답 끝.
- 브라우저에 "주문 접수됨" 메시지 표시.
- **총 소요 시간: ~0.2초.**

#### 단계 10. Orchestrator가 `order-created-event` 수신 [6장]

- `@KafkaListener(topics="order-created-event", groupId="orchestrator")`
- `states.put(42, new WorkflowState(42, 1, 1, "Seoul"))`
- 현재 Saga의 **컨텍스트**가 메모리에 저장됨.

#### 단계 11. Orchestrator가 `product-decrease-command` 발행 [5장, 6장]

- 결정적 messageId: `"saga-42-decrease"`
- 페이로드: `{messageId:"saga-42-decrease", orderId:42, productId:1, quantity:1}`
- 이름이 **현재/명령형**(decrease)이다. Command임을 이름이 증명.

#### 단계 12. Product 서비스가 명령 수신 → 멱등 체크 → 차감 [8장]

- `isDuplicate("saga-42-decrease")` 호출
- `processed_message_tb`에 INSERT 시도 → 성공 → `false` 반환
- 재고 조회: `quantity=10`
- 10 >= 1이므로 UPDATE: `quantity=9`

#### 단계 13. Product가 `product-decreased-event` 발행 [5장]

- 페이로드: `{orderId:42, productId:1, success:true}`
- 같은 `@Transactional` 범위 안에서 DB 커밋과 Kafka 전송이 함께 완료.

#### 단계 14. Orchestrator가 이벤트 수신 → 배송 생성 명령 [6장]

- `states.get(42)` → WorkflowState 확보
- `event.isSuccess()` == true → 성공 분기
- `delivery-create-command` 발행, messageId=`saga-42-create-delivery`
- 페이로드: `{messageId:"saga-42-create-delivery", orderId:42, address:"Seoul"}`

#### 단계 15. Delivery 서비스가 명령 수신 → 배송 생성 [8장]

- `isDuplicate("saga-42-create-delivery")` → `false`
- 주소 검증: `"Seoul"`는 blank 아님, 유효
- `INSERT INTO delivery_tb (order_id, address, status) VALUES (42, 'Seoul', 'READY')`
- delivery.id = 17

#### 단계 16. Delivery가 `delivery-created-event` 발행 + Orchestrator 대기

- 페이로드: `{orderId:42, deliveryId:17, success:true}`
- Orchestrator는 이 이벤트를 받고 **아무 명령도 발행하지 않는다**. 배송 완료 API 대기 상태.

#### 단계 17. 관리자가 배송 완료 API 호출 → Delivery 상태 변경 + 이벤트

- `PUT /api/deliveries/17/complete`
- `UPDATE delivery_tb SET status = 'COMPLETED' WHERE id = 17`
- `delivery-completed-event` 발행: `{orderId: 42}`
- Orchestrator 이벤트 수신 → `order-complete-command` 발행 (messageId=`saga-42-complete`)
- `states.remove(42)` ← Saga 종료, 메모리 정리

#### 단계 18. Order가 완료 명령 수신 → DB 업데이트 + 웹소켓 푸시 [8장, 10장]

- `isDuplicate("saga-42-complete")` → `false`
- `UPDATE order_tb SET status = 'COMPLETED' WHERE id = 42`
- `messagingTemplate.convertAndSend("/topic/orders/1", {orderId:42, status:"COMPLETED", message:"주문이 완료되었습니다."})`
- 브라우저의 웹소켓 세션이 STOMP MESSAGE 수신
- 화면에 **"🎉 주문이 완료되었습니다"** 표시

### 사용자가 체감한 시간표

| 시간 | 사용자 화면 |
|---|---|
| 0.0s | "주문하기" 버튼 클릭 |
| 0.2s | "주문 접수됨" 응답 (단계 9) |
| 0.3s | 스피너 사라짐 |
| ... | (배송 API 호출 대기. 교육용 수동 트리거) |
| N + 0.5s | 관리자가 배송 완료 API 호출 후 0.5초 이내로 |
| N + 0.5s | 🎉 "주문이 완료되었습니다" 알림 |

**사용자는 2번의 반응만 본다**. 첫 번째 즉시 응답과, 최종 완료 알림. 그 사이의 모든 Kafka 교통은 전혀 체감하지 못한다.

### 이 시나리오에서 호출된 모든 기술

| 기술 | 어디서 |
|---|---|
| K8s Ingress | 단계 2, 7 |
| K8s Service (Gateway) | 단계 2, 7 |
| K8s Deployment (서비스들) | 모든 단계 |
| JWT 발급·검증 | 단계 3, 7 |
| Kafka Producer | 단계 8, 11, 13, 14, 17 |
| Kafka Consumer | 단계 10, 12, 14, 15, 17, 18 |
| Kafka Topic 명명 규칙 | 모든 Kafka 단계 |
| Saga Orchestration | 단계 10, 14, 17 |
| 결정적 messageId | 단계 11, 14, 17 |
| 멱등성 체크 | 단계 12, 15, 18 |
| WebSocket / STOMP | 단계 5, 18 |

**책의 모든 장이 이 한 시나리오에서 활용된다**. 이것이 이 통합 장의 목적이다.

## 11.3 실패 시나리오 End-to-End — 배송 주소 실패

### 설정

- 사용자: **cos** (userId=2)
- 주문 상품: **MacBook Pro** (productId=1), 재고 10
- 배송지: **`""`** (빈 문자열 - 클라이언트 검증 우회되었다고 가정)
- 주문 ID = 43

### 전체 시퀀스 다이어그램

```
사용자    Order     Orch     Product    Delivery    Kafka
  │         │        │         │          │           │
  │ 주문     │        │         │          │           │
  │────────►│        │         │          │           │
  │         │─publish order-created──────────────────►│
  │◄ OK ────│        │         │          │           │
  │         │        │◄consume order-created           │
  │         │        │ states.put(43, addr="")         │
  │         │        │─publish product-decrease──────►│
  │         │        │         │          │           │
  │         │        │         │◄consume product-decrease
  │         │        │         │ isDup OK │           │
  │         │        │         │ 10→9 차감 │           │
  │         │        │         │─publish product-decreased(true)─►
  │         │        │◄consume product-decreased
  │         │        │ success │          │           │
  │         │        │─publish delivery-create────────►
  │         │        │         │          │           │
  │         │        │         │          │◄consume delivery-create
  │         │        │         │          │ isDup OK  │
  │         │        │         │          │ addr="" 검증 FAIL
  │         │        │         │          │ delivery INSERT 안 함
  │         │        │         │          │─publish delivery-created(false)─►
  │         │        │◄consume delivery-created
  │         │        │ success=false      │           │
  │         │        │ *** 보상 시작 ***                │
  │         │        │─publish product-increase──────►│
  │         │        │         │          │           │
  │         │        │         │◄consume product-increase
  │         │        │         │ isDup OK │           │
  │         │        │         │ 9→10 복구 │           │
  │         │        │                    │           │
  │         │        │─publish order-cancel──────────►│
  │         │        │ states.remove(43)              │
  │         │◄consume order-cancel                    │
  │         │ isDup OK                                 │
  │         │ UPDATE status=CANCELLED                  │
  │         │─── WS send "재고 부족으로 취소" ──►       │
  │◄━━━ MESSAGE: "재고 부족으로 주문이 취소되었습니다." ━━┤
```

### 주목할 것들

1. **재고가 9에서 10으로 되돌아왔다.** 최종 재고는 주문 전과 같다. 보상이 제대로 작동한 것.
2. **보상 순서**: `product-increase` 먼저, `order-cancel` 나중. **역순 원칙**.
3. **`processed_message_tb`**에는 4개의 messageId가 쌓인다:
   - `saga-43-decrease` (product)
   - `saga-43-create-delivery` (delivery)
   - `saga-43-increase` (product)
   - `saga-43-cancel` (order)
4. **사용자가 받은 최종 알림**은 `"재고 부족으로 주문이 취소되었습니다."`이다. 그러나 진짜 실패 원인은 **배송 주소**였다.

(4번에 대한 부가 설명: chap044의 현재 코드는 `OrderHandler.onCancel`의 웹소켓 메시지를 **재고 부족** 표현으로 고정해두었다. 프로덕션이라면 취소 원인을 명령에 실어 보내고, 원인별로 다른 메시지를 푸시해야 한다. 이것은 **교육용 단순화**의 한 예다.)

## 11.4 복합 시나리오 — 중복 메시지가 끼어든 경우

가장 극적인 시나리오를 상상해보자. 정상 흐름 중 **`product-decrease-command`가 실수로 두 번 발행**되었다.

### 설정

- 주문 44번, 같은 상품, 같은 수량.
- Orchestrator가 `product-decrease-command`를 **두 번** 쏘았다고 가정 (재시도 상황).

### 시퀀스 다이어그램 (멱등성이 보호함)

```
Orch  Kafka   Product
  │     │      │
  │─publish product-decrease (saga-44-decrease) ────►│
  │─publish product-decrease (saga-44-decrease) ────►│   ← 중복!
  │     │      │
  │     │      │◄consume 1st
  │     │      │  isDuplicate(saga-44-decrease)?
  │     │      │  → INSERT OK → false
  │     │      │  재고 차감: 10→9
  │     │      │  publish product-decreased(success=true)
  │     │      │
  │     │      │◄consume 2nd (같은 메시지)
  │     │      │  isDuplicate(saga-44-decrease)?
  │     │      │  → INSERT 시도 → PK 중복 → true
  │     │      │  🔁 중복 메시지 스킵
  │     │      │  (아무 일도 안 함)
  │     │      │
  │     │      │  (이벤트는 publish 안 함)
```

**결과**: 재고가 **한 번만** 차감됨 (정상). 이벤트도 **한 번만** 발행됨. 후속 흐름은 정상.

### 만약 멱등성이 없었다면

- 재고가 **두 번 차감** → 10 → 8
- `product-decreased-event`도 **두 번 발행**
- Orchestrator가 이벤트를 두 번 받으면:
  - 첫 번째: `states.get(44)` → 존재 → `delivery-create-command` 발행
  - 두 번째: `states.get(44)` → 아직 존재 → `delivery-create-command` **또** 발행
- Delivery는 같은 messageId의 명령을 두 번 받음
  - 첫 번째: 배송 INSERT
  - 두 번째: 멱등성 감지... **또 멱등성 필요!**

즉 **Delivery 쪽의 멱등성도** 있으므로 두 번째 배송 생성은 막힌다. 그러나 재고는 이미 잘못 차감되어 있다. **재고만 틀어진 채로 Saga는 성공**하는 기괴한 상황.

**결론**: 멱등성은 Command 체인의 **첫 번째 수신자**에서 막아야 한다. 뒤에서 막아도 부분적으로만 보호된다.

## 11.5 체크리스트 — 각 장에서 배운 것이 어디서 쓰였나

이 장을 읽고 난 뒤, 다음 체크리스트로 자기 이해를 점검해보자. 각 항목에 "그렇다"로 답할 수 있어야 한다.

- [ ] **1장**: 정상 시나리오에 **네 개의 비즈니스 서비스**가 모두 참여하는 것을 확인할 수 있다.
- [ ] **2장**: 사용자의 요청이 Ingress → Gateway → Service → Pod 순서로 흐르는 것을 설명할 수 있다.
- [ ] **3장**: 왜 사용자가 한 번만 로그인하면 여러 주문을 낼 수 있는지(JWT 무상태) 설명할 수 있다.
- [ ] **4장**: Kafka Producer와 Consumer가 서로 몰라도 통신이 되는 이유를 설명할 수 있다.
- [ ] **5장**: 9개의 토픽 중 무엇이 Command이고 무엇이 Event인지 이름만 보고 구분할 수 있다.
- [ ] **6장**: Orchestrator가 Saga 중 어떤 상태에서 어떤 명령을 발행하는지 추적할 수 있다.
- [ ] **7장**: 실패 시나리오에서 **역순 보상**이 어디서 일어나는지 지목할 수 있다.
- [ ] **8장**: `processed_message_tb`에 쌓이는 messageId들을 나열할 수 있다.
- [ ] **9장**: 왜 이벤트에는 멱등성 처리를 따로 안 하는지 설명할 수 있다.
- [ ] **10장**: 사용자가 받는 최종 알림이 어느 서비스에서 어느 메커니즘으로 오는지 설명할 수 있다.

10개 중 7개 이상을 "그렇다"로 답할 수 있으면 이 책의 본문을 잘 읽은 것이다. 모자란 항목은 해당 장으로 돌아가서 다시 읽고 시뮬레이터로 눈으로 확인하자.

## 11.6 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 정상 시나리오는 **18개의 명확한 단계**로 쪼갤 수 있다.
- 각 단계에서 어느 장의 지식이 쓰이는지 매핑할 수 있다.
- 실패 시나리오는 정상 흐름에서 **분기**되어 보상 Saga로 이어진다.
- 중복 메시지가 있어도 멱등성이 첫 수신자에서 막으면 전체 Saga는 온전하게 완성된다.
- 사용자는 **처음 응답**과 **마지막 웹소켓 알림**의 두 반응만 체감한다.

이제 책의 본문은 거의 끝났다. 다음 두 장은 보너스다. 시뮬레이터를 어떻게 사용해야 가장 많이 배우는지(12장), 그리고 이 교육용 코드를 프로덕션에 가져갈 때 무엇을 바꿔야 하는지(13장)다.

---

# 12장. 그림자극으로 다시 보기

## 12.1 시뮬레이터의 목적

`simulator-msa-04.html`은 이 책의 **시각적 부록**이다. 책을 읽고 개념을 이해했다면, 시뮬레이터는 그 개념을 **움직이는 그림**으로 보여준다. 텍스트 1000자와 동영상 10초 중 어느 쪽이 더 강한지는 읽는 사람 나름이지만, 둘 다 쓰면 학습 효과가 곱해진다.

시뮬레이터는 세 개의 탭을 갖는다.

1. **🏗 쿠버네티스 세상**: 인프라 수준의 관점
2. **🛒 MSA 비즈니스 흐름**: 비즈니스 로직 수준의 관점
3. **🚇 Ingress 터널 (Minikube)**: 로컬 개발 환경 특수 이슈

이 세 탭의 목적은 다르다. 책의 어느 장을 공부했느냐에 따라 어느 탭을 써야 할지도 다르다.

## 12.2 탭 1: 쿠버네티스 세상

### 이 탭이 보여주는 것

- 외부의 Browser
- 도시의 문 Ingress Controller Pod
- 클러스터 내부의 6개 Service (각 Service당 Pod 2개가 보임)
  - user-service
  - product-service
  - order-service
  - delivery-service
  - frontend-service
  - gateway-service
- orchestrator-service (별도 자리)
- kafka-service (KRaft 모드 단일 노드)
- db-service (MySQL)

### 이 탭의 사용법

#### 실험 1: 로그인 요청 추적 [3장]

"▶ 로그인 요청 1번" 버튼을 누른다.

관찰:
- **보라색** 점이 Browser → Ingress → gateway → user-service 순서로 이동.
- user-service의 Pod 중 **하나에만** 점이 가서 처리됨.
- 돌아오는 길에는 **JWT 배지**를 달고 이동.
- Browser의 상태가 🔓 → 🔐으로 바뀜.

학습 포인트: K8s Service는 여러 Pod 중 하나로 **로드밸런싱**한다.

#### 실험 2: 자가 치유 [2장]

"💀 Pod1 강제 종료" 버튼을 누른다.

관찰:
- 선택된 Pod이 빨갛게 변하고 사라진다.
- 몇 초 후, **자동으로 새 Pod이** 같은 Service 밑에 나타난다.
- 그동안 남은 Pod은 정상 작동 (요청이 들어오면 처리).

학습 포인트: Deployment의 `replicas: 2` 설정은 **항상 2개가 유지되도록** 자동 복구한다.

#### 실험 3: 로드 밸런싱 [2장]

"▶▶ 10번 반복" 버튼을 누른다.

관찰:
- 10개의 요청이 Service로 들어온다.
- 요청 점들이 **두 Pod에 5개씩** 나눠진다.
- 한 Pod만 바쁜 것이 아니라 **고르게 분산**된다.

학습 포인트: kube-proxy가 요청을 **라운드로빈 또는 랜덤**으로 분산한다.

#### 실험 4: 전체 주문 흐름 (자동)

"▶ 주문 전체 흐름" 버튼을 누른다.

관찰:
- 여러 색의 점들이 **다양한 서비스 사이로 이동**한다.
- 각 색은 서로 다른 **통신 종류**를 나타낸다.
  - 보라: 로그인/JWT
  - 회색: REST
  - 황색: Kafka 메시지
  - 초록: WebSocket
  - 빨강: 에러

학습 포인트: 하나의 주문 완료까지 **수많은 통신**이 있지만, 사용자는 두세 번의 반응만 체감한다.

## 12.3 탭 2: MSA 비즈니스 흐름

### 이 탭이 보여주는 것

- 브라우저 모형 (실제 로그인 폼, 주문 폼, 결과 표시)
- 4개 비즈니스 서비스 박스
- Orchestrator 박스
- Kafka (9개 토픽 슬롯 포함)
- DB 패널 (4개 테이블 실시간 상태)

### 이 탭의 사용법

#### 실험 1: Happy Path — 정상 흐름 추적 [6장, 11장]

"▶ Happy Path" 버튼을 누른다.

관찰할 순서:
1. 브라우저에서 "주문" 액션 발생.
2. Order 박스에 데이터 수신 → DB 패널 `order_tb`에 새 행 추가 (status=PENDING).
3. `order-created-event` 토픽에 🟡 점 등장.
4. Orchestrator가 점을 집어감.
5. `product-decrease-command` 토픽에 🟡 점 등장.
6. Product가 처리 → DB 패널 `product_tb`의 quantity가 10 → 9.
7. DB 패널 `processed_message_tb`에 **1번째 행** 등장.
8. `product-decreased-event` 점 등장, Orchestrator가 가져감.
9. `delivery-create-command` 점 등장 → Delivery 처리.
10. `delivery_tb`에 새 행 등장, `processed_message_tb`에 **2번째 행**.
11. `delivery-created-event` → Orchestrator 대기 상태.
12. (수동 또는 자동으로) 배송 완료 API 호출.
13. `delivery-completed-event` → Orchestrator → `order-complete-command`.
14. Order 처리 → `order_tb`의 status가 PENDING → COMPLETED.
15. `processed_message_tb`에 **3번째 행**.
16. 브라우저 알림: 🎉 "주문이 완료되었습니다."

**체크리스트**:
- [ ] 9개 토픽 슬롯 중 **5개**에 점이 지나갔다 (`order-created`, `product-decrease`, `product-decreased`, `delivery-create`, `delivery-created`, `delivery-completed`, `order-complete` → 실제로 7개).
- [ ] `processed_message_tb`에 **3개의 행**이 쌓였다.
- [ ] `product_tb`의 재고가 **1개 감소**했다.
- [ ] `order_tb`의 상태가 **2번 변화**했다 (PENDING 생성, COMPLETED 업데이트).

#### 실험 2: Fail Path — 재고 부족 시나리오 [7장]

iPhone 15(재고 0)를 선택하고 "▶ Fail Path" 버튼을 누른다.

관찰:
- `order-created-event` 발행 → Product가 차감 시도 → **실패**.
- `product-decreased-event`가 **실패 상태**로 발행 (색 또는 테두리로 표시).
- Orchestrator가 `order-cancel-command` 즉시 발행.
- **배송 관련 토픽은 건드리지 않는다**.
- Order 상태: PENDING → CANCELLED.
- 브라우저 알림: 💥 "재고 부족으로 주문이 취소되었습니다."

**체크리스트**:
- [ ] `product-decrease-command`는 실행됐지만 **재고 변화 없음**.
- [ ] `delivery-*` 토픽은 **전혀 건드리지 않음**.
- [ ] `processed_message_tb`에 **2개의 행**이 쌓임 (`decrease`, `cancel`).
- [ ] `order_tb`의 상태가 **CANCELLED**로 끝남.

#### 실험 3: Cancel Path — 배송 후 취소 시나리오 (또는 배송 실패)

"✖ Cancel Path" 버튼을 누른다. 이 경로는 chap044의 구현에 따라 달라질 수 있지만, **보상 Saga**의 풀 파워를 보여준다.

관찰:
- 재고 차감 성공 → 배송 생성 실패(또는 취소) → **보상 시작**.
- `product-increase-command` 발행, 재고 복구.
- `order-cancel-command` 발행, 주문 취소.
- `product_tb`의 quantity가 **10 → 9 → 10**으로 **왕복**.

**체크리스트**:
- [ ] 재고의 **왕복 움직임**을 눈으로 확인.
- [ ] `processed_message_tb`에 **4개의 행**이 쌓임.
- [ ] 보상 순서가 **역순**임을 확인 (increase가 cancel보다 먼저).

## 12.4 탭 3: Ingress 터널

### 이 탭이 보여주는 것

- Host Machine (사용자 PC)
- Browser → `/etc/hosts`
- Minikube VM (Docker driver 내부)
- K8s Node → Ingress → Service → Pod

### 이 탭의 사용법

이 탭은 **개념 이해용**이다. 버튼이 많지 않을 수 있다.

관찰할 포인트:
- Host Machine과 Minikube VM 사이에 **벽**이 그려져 있다.
- 이 벽을 뚫는 것이 **`minikube tunnel`** 프로세스.
- `/etc/hosts`에 `127.0.0.1 metacoding.local`이 있어야 함을 강조.

이 탭을 본 뒤 독자는 다음 질문에 답할 수 있어야 한다.

- Q1. 왜 Minikube는 호스트에서 직접 접근할 수 없는가?
- A1. Docker driver로 실행되면 클러스터 전체가 **하나의 컨테이너** 안에 갇히기 때문.

- Q2. `/etc/hosts`와 `minikube tunnel`은 왜 둘 다 필요한가?
- A2. `/etc/hosts`는 DNS를 속이는 것, `minikube tunnel`은 포트를 실제로 연결하는 것. 둘 다 있어야 브라우저가 `metacoding.local`로 접근할 수 있음.

## 12.5 시뮬레이터 vs 실제 코드

시뮬레이터는 **동작을 단순화**해서 보여준다. 실제 코드와 다른 점이 몇 가지 있다.

| 항목 | 시뮬레이터 | 실제 chap044 |
|---|---|---|
| Kafka 메시지 도달 시간 | 100~500ms (애니메이션용) | 10~50ms |
| 중복 메시지 발생 | 수동 트리거 | 실제로는 거의 없음 (안정된 환경) |
| Pod 재생성 시간 | 2~3초 | 20~60초 |
| 배송 완료 API | 자동 또는 버튼 | 관리자가 수동 호출 |

시뮬레이터는 **개념 이해용**이지 **성능 측정용**이 아님을 기억하자.

## 12.6 이 장을 덮으며

이 장에서 우리는 이렇게 배웠다.
- 시뮬레이터는 세 탭으로 구성된다: K8s 인프라, MSA 비즈니스, Minikube 터널.
- **탭 1**은 로드밸런싱·자가 치유·전체 통신을 **인프라 관점**에서 본다.
- **탭 2**는 Saga·Kafka·멱등성을 **비즈니스 관점**에서 본다. 가장 중요한 탭.
- **탭 3**은 로컬 개발 환경의 네트워크 이슈를 설명한다.
- 시뮬레이터의 타이밍은 애니메이션용이고, 실제 성능 지표가 아니다.

이제 마지막 장이다. 이 교육용 프로젝트를 **실제 프로덕션**에 가져가려면 무엇을 바꿔야 하는지 정리한다.

---

# 13장. 프로덕션으로 가려면

## 13.1 이 장의 목적

chap044는 **교육용**이다. 여러 곳에서 **의도적으로 단순화**했다. 그 단순화가 학습을 쉽게 해주지만, **그대로 프로덕션에 올리면 재앙**이다. 이 장에서는 각 단순화가 무엇이었는지, 그리고 실제 프로덕션에서는 어떻게 해야 하는지 정리한다.

이 장은 **에필로그이자 다음 학습의 출발점**이다. 여기서 언급되는 주제들은 그 자체로 각자 한 권의 책이 있을 만큼 깊다. 이 장의 역할은 "아, 이런 주제가 있구나"라는 **지도**를 그려주는 것이다.

## 13.2 단순화 목록과 그 대안

### 1. 공유 DB → Database per Service

**chap044의 선택**: 모든 서비스가 하나의 MySQL을 공유.

**이유**: 학습자가 "서비스별 DB"라는 추가 복잡성을 피하기 위해. Docker Compose나 K8s 매니페스트가 단순해짐.

**프로덕션 방식**:
- 각 서비스마다 **독립적인 DB**를 둔다.
- 서비스 간 데이터 조회는 **API 호출**이나 **Read Model 복제**로.
- 스키마 변경이 다른 서비스에 영향을 주지 않는다.

**장애물**:
- 서비스 간 조인이 불가능해 API로 풀어야 함.
- 분산 트랜잭션이 필요해짐 → 이 책의 Saga가 등장하는 이유.

### 2. In-memory Orchestrator State → 외부 상태 저장소

**chap044의 선택**: `OrderOrchestrator.states`를 `ConcurrentHashMap`으로 관리.

**이유**: 외부 저장소 도입은 Redis·DB 운영 부담을 추가하므로.

**프로덕션 방식**:
- **Redis**로 Saga 상태 저장 (TTL 활용).
- 또는 **DB 테이블 `saga_state_tb`** 만들어 저장.
- Orchestrator가 여러 Pod으로 스케일 아웃 가능해짐 (상태가 공유되므로).

**장애물**:
- Redis 장애 시 Saga 진행이 정지.
- 상태 직렬화·역직렬화 스키마 관리.

### 3. At-least-once → Transactional Outbox + Exactly-once

**chap044의 선택**: 기본 Kafka 설정(at-least-once)에 의존하고 멱등성으로 중복 흡수.

**이유**: Transactional Outbox의 구현 복잡성을 피하고 **개념 이해**에 집중.

**프로덕션 방식**:

```
서비스 로직 (DB 트랜잭션 안에서)
   │
   ├─ 비즈니스 INSERT/UPDATE
   └─ outbox_tb에 "보낼 메시지" INSERT
      (같은 트랜잭션)

별도 프로세스 (Debezium 같은 CDC)
   │
   ├─ outbox_tb를 폴링 또는 CDC로 감시
   ├─ Kafka로 publish
   └─ 성공 시 outbox 레코드 삭제
```

이 패턴의 효과:
- 비즈니스 DB 커밋과 Kafka 전송이 **원자적**.
- 전송 실패해도 outbox에 남아 있어 재시도 가능.
- 메시지 손실 방지.

**장애물**:
- Debezium 같은 CDC 인프라 추가.
- outbox_tb 관리 (TTL 등).

### 4. JWT 검증을 매 서비스마다 → Gateway에서 일괄 검증

**chap044의 선택**: Order 서비스가 직접 JWT 검증.

**이유**: JWT 검증 로직을 **학습자가 한 곳에서 명확히 볼 수 있도록**.

**프로덕션 방식**:
- **API Gateway**(Spring Cloud Gateway, Kong, Envoy 등)가 **모든 요청의 JWT를 검증**.
- 검증 후 `X-User-Id` 같은 헤더로 바꿔서 내부 서비스로 전달.
- 내부 서비스는 헤더만 신뢰하면 됨 (JWT 로직 불필요).

**장애물**:
- Gateway의 JWT 라이브러리와 User 서비스의 JWT 발급 로직 **버전 호환성**.
- 비밀키 중앙 관리 필요.

### 5. 수동 배송 완료 API → 배송사 웹훅

**chap044의 선택**: 관리자가 `PUT /api/deliveries/{id}/complete`를 수동 호출.

**이유**: 실제 배송사 연동은 복잡. 수동으로 **이벤트를 트리거**할 수 있는 편의.

**프로덕션 방식**:
- 배송사(쿠팡, CJ대한통운 등)의 **웹훅**을 받아 Delivery 서비스가 자동 처리.
- 웹훅 서명 검증 필수.
- 웹훅이 중복 도착할 수 있으므로 **멱등성 처리** 추가.

**장애물**:
- 배송사마다 API 스펙이 다름.
- 웹훅 미도착 시 폴링 폴백 로직 필요.

### 6. 단일 Kafka Broker → 3-Broker + ISR

**chap044의 선택**: 단일 Broker, replicas 1.

**이유**: 학습 환경에서 3개 Broker를 돌리는 것은 리소스 낭비.

**프로덕션 방식**:
- **3개 이상의 Broker** (쿼럼 과반수 합의 가능).
- 각 Topic의 **복제 계수(replication.factor)를 3**으로 설정.
- **min.insync.replicas=2**: 2개 이상의 복제본이 동기화된 상태에서만 쓰기 성공.

**장애물**:
- Broker 간 네트워크 구성.
- 디스크 용량 3배.

### 7. Kafka 토픽 자동 생성 → 명시적 생성

**chap044의 선택**: `auto.create.topics.enable=true`에 의존.

**이유**: 토픽을 미리 만드는 단계가 귀찮음.

**프로덕션 방식**:
- `auto.create.topics.enable=false` 설정.
- 각 토픽을 **명시적으로 생성**하면서 파티션 수·복제 계수 설정.
- **Infrastructure as Code**(Terraform 등)로 관리.

**장애물**:
- 오타로 토픽 이름이 잘못되면 메시지가 조용히 무시 (오탐지).
- 토픽 수가 많아지면 관리 오버헤드.

### 8. HTTP만 사용 → HTTPS + mTLS

**chap044의 선택**: 모든 통신 HTTP.

**이유**: 인증서 관리 복잡.

**프로덕션 방식**:
- Ingress에서 **TLS 종료**.
- 내부 서비스 간 통신도 **mTLS**(Mutual TLS) 적용.
- **Service Mesh**(Istio, Linkerd)로 자동화.

**장애물**:
- 인증서 관리·갱신.
- 성능 오버헤드.

### 9. 관찰성 없음 → Observability 풀스택

**chap044의 선택**: 기본 Spring Boot 로그만.

**이유**: 책의 주제가 관찰성이 아님.

**프로덕션 방식**:
- **Metrics**: Prometheus + Grafana (Kafka lag, 응답 시간, 에러율 등).
- **Logs**: ELK 스택 또는 Loki로 중앙 집계.
- **Traces**: **OpenTelemetry**로 분산 추적 (한 요청이 여러 서비스를 거치는 경로 추적).
- **Dashboards**: SLO 기반 모니터링.

**장애물**:
- 관찰성 인프라의 운영 부담.
- 계측 코드 추가.

### 10. 보안 강화 없음 → 기본 보안 원칙 적용

**chap044의 선택**: WebSocket 구독 권한 미검증, Secret 기본 사용, SQL Injection 방어 미강조.

**프로덕션 방식**:
- WebSocket 구독 시 JWT 검증 (이 사용자가 이 topic을 구독할 권한이 있나?).
- Secret은 **KMS** 또는 **HashiCorp Vault**로 관리.
- JPA 사용으로 SQL Injection 자동 방어 확보(대부분).
- **최소 권한 원칙**: 각 Pod의 Service Account에 꼭 필요한 권한만.

## 13.3 chap044의 "단순화 ↔ 프로덕션" 매핑 요약표

| 영역 | chap044 | 프로덕션 대안 |
|---|---|---|
| DB | 공유 MySQL | Database per Service |
| Saga 상태 | In-memory Map | Redis/DB |
| 메시지 신뢰성 | At-least-once + 멱등성 | + Transactional Outbox |
| 인증 | 각 서비스 자체 검증 | Gateway 일괄 검증 |
| 배송 트리거 | 수동 API | 배송사 웹훅 |
| Kafka 규모 | 단일 Broker | 3+ Broker, ISR |
| Topic 생성 | 자동 | 명시적, IaC |
| 통신 보안 | HTTP | HTTPS + mTLS |
| 관찰성 | 기본 로그 | Prometheus + Loki + OTel |
| 보안 | 기본 | KMS, 최소 권한, WebSocket 권한 |

## 13.4 다음 학습 경로

이 책을 마스터했다면 다음 주제로 나아가라.

### 실무 기술

- **Spring Cloud OpenFeign**: 서비스 간 REST 호출을 깔끔하게.
- **Resilience4j**: Circuit Breaker, Retry, Timeout 패턴.
- **Spring Cloud Config**: 설정 중앙화.
- **Spring Cloud Gateway**: Reactive API Gateway.
- **Spring Cloud Sleuth + Zipkin**: 분산 추적 (또는 OpenTelemetry로 전환).

### 인프라 기술

- **Helm**: K8s 매니페스트의 템플릿 관리.
- **ArgoCD**: GitOps 기반 배포.
- **Istio**: Service Mesh로 mTLS·트래픽 관리 자동화.
- **KEDA**: 이벤트 기반 Auto-scaling.
- **Strimzi**: Kafka 오퍼레이터로 K8s에서 Kafka 안정적 운영.

### 고급 패턴

- **CQRS + Event Sourcing**: 쓰기와 읽기를 완전히 분리, 이벤트를 source of truth로.
- **Transactional Outbox Pattern**: 위에서 언급.
- **CDC (Change Data Capture)**: Debezium.
- **Schema Registry (Confluent)**: Kafka 메시지 스키마 강제.
- **Saga Framework**: Axon, Eventuate 같은 프레임워크.

### 읽을 만한 책·문서

- "Designing Data-Intensive Applications" (Martin Kleppmann) — 분산 시스템의 바이블.
- "Microservices Patterns" (Chris Richardson) — Saga·CQRS 등 패턴.
- "Kafka: The Definitive Guide" — Kafka 깊이.
- Martin Fowler의 [microservices article](https://martinfowler.com/articles/microservices.html) — 고전.

## 13.5 이 책을 덮으며

이 책을 다 읽은 당신은 이제 다음을 할 수 있다.

- 마이크로서비스 아키텍처의 **이유와 대가**를 설명할 수 있다.
- Kafka의 Topic·Partition·Offset·Consumer Group을 **개념적으로** 이해한다.
- Saga 패턴의 오케스트레이션과 코레오그래피를 구분하고, 어느 상황에 어떤 걸 쓸지 **판단**할 수 있다.
- 멱등성이 왜 필요한지, 어떻게 구현하는지, 어느 메시지에 적용해야 하는지 **설명**할 수 있다.
- 분산 트랜잭션의 실패 시나리오에서 보상 Saga가 **역순으로** 돌아가는 것을 추적할 수 있다.
- 이 교육용 코드가 왜 "교육용"인지, 프로덕션에 가려면 무엇이 부족한지 **진단**할 수 있다.

프롤로그의 다섯 가지 질문에 이제 답해보자.

**질문 1.** 재고가 부족해서 차감이 실패했다. 그런데 이미 주문은 PENDING 상태로 저장되어 있다. 이 주문은 어떻게 되나?
→ Orchestrator가 `product-decreased-event(success=false)`를 받고 `order-cancel-command`를 발행해서 **CANCELLED로 변경**한다. 사용자에게는 웹소켓으로 취소 알림이 간다.

**질문 2.** 배송 생성까지 성공했는데 갑자기 서버가 죽었다. 재고는 이미 차감되어 있다. 누가 되돌리나?
→ Orchestrator가 `delivery-created-event(success=false)` 또는 후속 실패를 받으면 **`product-increase-command`로 재고를 복구**한다. 단, **Orchestrator의 상태가 in-memory**라는 한계 때문에 Orchestrator 자체가 죽으면 복구되지 않는다. 프로덕션에서는 Saga 상태 저장소를 두어야 한다 (13.2의 #2).

**질문 3.** Kafka가 같은 명령을 두 번 전달했다. 재고가 두 번 차감되면 재고가 음수가 된다. 이걸 어떻게 막나?
→ `processed_message_tb`에 결정적 `messageId`(`saga-{orderId}-{action}`)를 기록하고 **PK 중복으로 중복 감지**한다. 두 번째 명령은 `isDuplicate`에서 스킵된다.

**질문 4.** 오케스트레이터 서비스 자체가 죽었다. 절반쯤 진행된 주문들은 어떻게 되나?
→ **치명적 문제다.** chap044는 in-memory 상태이므로 진행 중이던 Saga들이 유실된다. 프로덕션에서는 상태를 Redis/DB에 저장해서 재시작 시 복구해야 한다. 또한 Orchestrator를 **여러 Pod으로 스케일**해서 가용성을 높여야 한다.

**질문 5.** 사가 패턴과 멱등성은 무엇인가?
→ **사가 패턴**은 분산 트랜잭션을 여러 로컬 트랜잭션의 연쇄로 나누고, 실패 시 **보상 트랜잭션**으로 수습하는 패턴이다. 오케스트레이션(지휘자)과 코레오그래피(눈치 보기) 두 스타일이 있다. **멱등성**은 같은 연산을 여러 번 해도 결과가 같은 성질이고, Kafka의 at-least-once 환경에서 **중복 메시지를 흡수**하기 위해 필수다.

이로써 프롤로그에서 연 닫지 않은 문을 모두 닫았다. 이 책이 끝났다.

그러나 이 지식은 **한 번 읽고 끝나는 것**이 아니다. 실제 프로젝트에서 이 패턴들을 써보고, 실패하고, 다시 해보면서 몸에 붙인다. 그때 이 책의 장들이 한 번씩 기억에 떠오를 것이다. 그리고 프롤로그의 그 0.3초의 침묵이, 이제는 단순한 로딩이 아니라 **아름답게 설계된 교향곡**으로 들릴 것이다.

---

# 부록 A. 토픽·메시지 전수표

## A.1 Kafka 토픽 9개 전수표

| # | 토픽 이름 | 종류 | 발행자 | 구독 Group | DTO |
|---|---|---|---|---|---|
| 1 | `order-created-event` | Event | Order | `orchestrator` | `OrderCreatedEvent` |
| 2 | `product-decrease-command` | Command | Orchestrator | `product-service` | `ProductDecreaseCommand` |
| 3 | `product-decreased-event` | Event | Product | `orchestrator` | `ProductDecreasedEvent` |
| 4 | `product-increase-command` | Command | Orchestrator | `product-service` | `ProductIncreaseCommand` |
| 5 | `delivery-create-command` | Command | Orchestrator | `delivery-service` | `DeliveryCreateCommand` |
| 6 | `delivery-created-event` | Event | Delivery | `orchestrator` | `DeliveryCreatedEvent` |
| 7 | `delivery-completed-event` | Event | Delivery | `orchestrator` | `DeliveryCompletedEvent` |
| 8 | `order-complete-command` | Command | Orchestrator | `order-service` | `OrderCompleteCommand` |
| 9 | `order-cancel-command` | Command | Orchestrator | `order-service` | `OrderCancelCommand` |

## A.2 DTO 필드 전수표

### Event DTOs

**`OrderCreatedEvent`**
- `orderId: Integer`
- `userId: Integer`
- `productId: Integer`
- `quantity: Integer`
- `address: String`

**`ProductDecreasedEvent`**
- `orderId: Integer`
- `productId: Integer`
- `success: boolean`

**`DeliveryCreatedEvent`**
- `orderId: Integer`
- `deliveryId: Integer` (null if failed)
- `success: boolean`

**`DeliveryCompletedEvent`**
- `orderId: Integer`

### Command DTOs (모두 `messageId` 포함)

**`ProductDecreaseCommand`**
- `messageId: String`
- `orderId: Integer`
- `productId: Integer`
- `quantity: Integer`

**`ProductIncreaseCommand`**
- `messageId: String`
- `orderId: Integer`
- `productId: Integer`
- `quantity: Integer`

**`DeliveryCreateCommand`**
- `messageId: String`
- `orderId: Integer`
- `address: String`

**`OrderCompleteCommand`**
- `messageId: String`
- `orderId: Integer`

**`OrderCancelCommand`**
- `messageId: String`
- `orderId: Integer`

## A.3 messageId 네이밍 전수표

| messageId 패턴 | 의미 |
|---|---|
| `saga-{orderId}-decrease` | 재고 차감 명령 |
| `saga-{orderId}-increase` | 재고 복구 명령 (보상) |
| `saga-{orderId}-create-delivery` | 배송 생성 명령 |
| `saga-{orderId}-complete` | 주문 완료 명령 |
| `saga-{orderId}-cancel` | 주문 취소 명령 (보상 또는 실패 시) |

## A.4 `processed_message_tb` 샘플 데이터

정상 주문 42번 완료 시:

| message_id | service_name | processed_at |
|---|---|---|
| `saga-42-decrease` | product | 2026-04-17 14:30:15 |
| `saga-42-create-delivery` | delivery | 2026-04-17 14:30:16 |
| `saga-42-complete` | order | 2026-04-17 14:32:45 |

재고 부족으로 실패한 주문 43번:

| message_id | service_name | processed_at |
|---|---|---|
| `saga-43-decrease` | product | 2026-04-17 14:35:02 |
| `saga-43-cancel` | order | 2026-04-17 14:35:03 |

배송 주소 실패로 보상된 주문 44번:

| message_id | service_name | processed_at |
|---|---|---|
| `saga-44-decrease` | product | 2026-04-17 14:40:10 |
| `saga-44-create-delivery` | delivery | 2026-04-17 14:40:11 |
| `saga-44-increase` | product | 2026-04-17 14:40:12 |
| `saga-44-cancel` | order | 2026-04-17 14:40:13 |

---

# 부록 B. DB 스키마 ERD

## B.1 테이블 목록

| 테이블 | 소유 서비스 (논리적) | 행 수 예시 |
|---|---|---|
| `user_tb` | User | 3 (ssar, cos, love) |
| `product_tb` | Product | 3 (MacBook Pro, iPhone 15, AirPods) |
| `order_tb` | Order | N (주문 수만큼) |
| `delivery_tb` | Delivery | N (배송 수만큼) |
| `processed_message_tb` | 모든 서비스 공유 | 주문당 2~4건 |

## B.2 테이블 스키마 상세

### user_tb
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | INT PK | AUTO_INCREMENT |
| username | VARCHAR(50) UNIQUE | 로그인 ID |
| email | VARCHAR(50) | 이메일 |
| password | VARCHAR(50) | 평문 (교육용) |
| roles | VARCHAR(50) | 권한 (USER 등) |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### product_tb
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | INT PK | |
| product_name | VARCHAR(50) | |
| quantity | INT | **재고 수량** |
| price | BIGINT | 원 단위 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### order_tb
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | INT PK | |
| user_id | INT | FK to user_tb (느슨한 참조) |
| product_id | INT | FK to product_tb |
| quantity | INT | 주문 수량 |
| status | VARCHAR(50) | PENDING / COMPLETED / CANCELLED |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### delivery_tb
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | INT PK | |
| order_id | INT | FK to order_tb |
| address | VARCHAR(100) | 배송 주소 |
| status | VARCHAR(50) | READY / COMPLETED |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### processed_message_tb
| 컬럼 | 타입 | 설명 |
|---|---|---|
| message_id | VARCHAR(100) PK | `saga-{orderId}-{action}` 형식 |
| service_name | VARCHAR(30) | product / order / delivery |
| processed_at | DATETIME | 처리 시각 |

## B.3 관계 다이어그램

```
user_tb (1) ───< (N) order_tb
                     │
                     │ (1)
                     │
                     │
                     ▼ (1)
                  delivery_tb


product_tb (1) ───< (N) order_tb (product_id 참조)


processed_message_tb (모든 서비스에서 PK로 중복 방지)
```

**중요**: 이 프로젝트는 **외래 키 제약조건(FK constraint)**을 DB 레벨에서 강제하지 않는다. `user_id`, `product_id`, `order_id`는 모두 **애플리케이션 레벨 참조**다. 그 이유는 마이크로서비스 환경에서는 DB가 논리적으로 쪼개진다는 전제를 갖기 때문이다. FK가 없으므로 주문 생성 시 실제로 user가 존재하는지 검증하는 책임은 애플리케이션에 있다.

## B.4 테스트 데이터

```sql
-- users
(1, 'ssar', 'ssar@metacoding.com', '1234', 'USER', ...)
(2, 'cos', 'cos@metacoding.com', '1234', 'USER', ...)
(3, 'love', 'love@metacoding.com', '1234', 'USER', ...)

-- products
(1, 'MacBook Pro', 10, 2500000, ...)  ← 정상 시나리오용
(2, 'iPhone 15', 0, 1300000, ...)    ← 재고 부족 시나리오용
(3, 'AirPods', 10, 300000, ...)      ← 정상 시나리오용
```

---

# 부록 C. 용어집

책에 등장한 기술 용어를 가나다 순으로 정리한다.

## ㄱ

**가용성(Availability)** — 시스템이 정상적으로 응답하는 시간의 비율. 99.9%(년 8시간 다운), 99.99%(년 52분 다운) 등으로 표현.

**게이트웨이(Gateway)** — 클라이언트 요청의 단일 진입점으로, 라우팅·인증·로깅 등을 담당. chap044에서는 Nginx.

**결정적 ID(Deterministic ID)** — 같은 의도의 메시지에 항상 같은 ID를 부여하는 방식. chap044는 `saga-{orderId}-{action}` 형식.

**결합도(Coupling)** — 서비스·컴포넌트들이 서로 얼마나 의존하는가. 낮을수록 좋음(loose coupling).

**경계 컨텍스트(Bounded Context)** — DDD에서 같은 용어가 서로 다른 의미를 갖는 단위. 마이크로서비스의 분리 기준.

## ㄴ

**네이밍 규칙(Naming Convention)** — 토픽·메시지·변수 등의 이름을 일관되게 짓는 규칙. chap044는 `<entity>-<verb>-command`와 `<entity>-<past-verb>-event`.

**네임스페이스(Namespace)** — K8s에서 리소스를 논리적으로 분리하는 단위. chap044는 `metacoding`.

## ㄷ

**데드락(Deadlock)** — 여러 작업이 서로를 기다리며 진행되지 않는 상태. Saga에서 실패 이벤트를 발행 안 하면 발생.

**디커플링(Decoupling)** — 서비스 간 의존성을 줄이는 것. Kafka의 Pub-Sub 모델이 대표적.

## ㄹ

**로드 밸런싱(Load Balancing)** — 요청을 여러 인스턴스에 분산하는 것. K8s의 Service가 자동으로 수행.

## ㅁ

**멱등성(Idempotency)** — 같은 연산을 여러 번 수행해도 결과가 같은 성질. `f(f(x)) = f(x)`.

**메시지 브로커(Message Broker)** — Producer와 Consumer 사이의 중계자. Kafka, RabbitMQ 등.

**모놀리식(Monolithic)** — 모든 기능이 하나의 애플리케이션에 들어간 구조. 마이크로서비스의 반대.

## ㅂ

**분산 트랜잭션(Distributed Transaction)** — 여러 서비스·DB에 걸친 트랜잭션. 2PC 또는 Saga로 해결.

**보상 트랜잭션(Compensating Transaction)** — 이미 커밋된 트랜잭션의 효과를 상쇄하는 트랜잭션. Saga의 핵심.

**브로커(Broker)** — Kafka의 서버 인스턴스. 메시지를 저장·중계.

## ㅅ

**사가(Saga)** — 분산 트랜잭션을 여러 로컬 트랜잭션의 연쇄로 나눈 패턴. 코레오그래피와 오케스트레이션 두 스타일.

**상태 머신(State Machine)** — 유한한 상태들 사이의 전이로 이루어진 모델. Orchestrator의 로직 구조.

**서비스(Service)** — K8s에서 여러 Pod을 하나의 고정 주소로 묶는 리소스. 로드 밸런서 역할도 함.

**세션 기반 인증(Session-based Auth)** — 서버가 세션 상태를 저장하는 인증 방식. JWT의 반대.

**소식지(Event)** — chap044의 비유 용어. 과거형으로 끝나는 Kafka 메시지.

**수평 확장(Horizontal Scaling, Scale Out)** — 인스턴스 수를 늘려 처리량을 증가시키는 확장.

**시퀀스 다이어그램(Sequence Diagram)** — 시간 순서로 메시지 흐름을 표현하는 UML 다이어그램.

## ㅇ

**오프셋(Offset)** — Kafka 파티션 내 메시지의 순번. Consumer가 관리.

**오케스트레이션(Orchestration)** — 중앙 조정자(Orchestrator)가 모든 단계를 지시하는 Saga 스타일.

**원자성(Atomicity)** — 트랜잭션의 ACID 중 A. 모두 성공하거나 모두 실패.

**웹소켓(WebSocket)** — 양방향 지속 연결을 제공하는 프로토콜. 서버가 클라이언트에 푸시 가능.

**이벤트(Event)** — 이미 일어난 사실을 알리는 메시지. 과거형으로 이름.

**이벤트 기반(Event-driven)** — 서비스가 이벤트에 반응해 동작하는 아키텍처.

## ㅈ

**자가 치유(Self-healing)** — K8s Deployment가 죽은 Pod을 자동으로 복구하는 능력.

**전달 보장(Delivery Guarantee)** — 메시지 전달의 보장 수준. at-most-once, at-least-once, exactly-once.

**지휘자(Orchestrator)** — chap044의 비유 용어. Saga의 중앙 조정자.

## ㅊ

**초당 처리량(Throughput)** — 시스템이 단위 시간에 처리하는 메시지/요청 수.

## ㅋ

**카프카(Kafka)** — 분산 메시지 브로커. LinkedIn에서 만들어 오픈소스화.

**컨슈머 그룹(Consumer Group)** — 같은 그룹 ID의 컨슈머들이 파티션을 나눠 가지며 수평 확장.

**코레오그래피(Choreography)** — 서비스끼리 이벤트를 직접 주고받는 Saga 스타일. 중앙 조정자 없음.

**쿠버네티스(Kubernetes, K8s)** — 컨테이너 오케스트레이션 플랫폼.

## ㅌ

**토픽(Topic)** — Kafka 메시지의 카테고리. chap044는 9개의 토픽을 사용.

**트랜잭션(Transaction)** — 원자적으로 수행되어야 하는 작업 단위.

## ㅍ

**파티션(Partition)** — Kafka Topic의 물리적 분할. 같은 키의 메시지는 같은 파티션으로.

**포드(Pod)** — K8s의 최소 실행 단위. 1개 이상의 컨테이너 포함.

**폴링(Polling)** — 클라이언트가 주기적으로 서버에 조회하는 방식. WebSocket의 대안.

**퍼블리시-서브스크라이브(Publish-Subscribe)** — 발행자가 토픽에 메시지를 올리면 구독자들이 받는 모델.

## ㅎ

**핸들러(Handler)** — chap044에서 `@KafkaListener` 메서드를 담은 클래스. ProductHandler, OrderHandler 등.

## 영문

**ACID** — Atomicity, Consistency, Isolation, Durability. 전통적 DB 트랜잭션의 4대 원칙.

**API Gateway** — 클라이언트 요청의 단일 진입점 (위 "게이트웨이" 참고).

**BASE** — Basically Available, Soft state, Eventual consistency. MSA의 패러다임.

**BASE vs ACID** — BASE는 일시적 불일치를 허용하고 시간이 지나면 일관성에 도달. MSA에 적합.

**CQRS** — Command Query Responsibility Segregation. 쓰기와 읽기의 책임 분리.

**CRUD** — Create, Read, Update, Delete. 기본 데이터 연산.

**DLQ (Dead Letter Queue)** — 처리 실패한 메시지를 모아두는 별도 큐. chap044는 없음.

**ERD (Entity Relationship Diagram)** — 데이터 모델 다이어그램. 부록 B 참고.

**HMAC** — Hash-based Message Authentication Code. JWT의 서명 알고리즘.

**HTTP** — HyperText Transfer Protocol. 기본 웹 프로토콜.

**Ingress** — K8s에서 클러스터 외부의 HTTP 트래픽을 내부 서비스로 라우팅하는 리소스.

**ISR (In-Sync Replicas)** — Kafka에서 리더와 동기화된 복제본. 쓰기 성공 조건.

**JPA (Java Persistence API)** — 자바 ORM 표준.

**JSON (JavaScript Object Notation)** — 데이터 교환 포맷.

**JWT (JSON Web Token)** — 서명된 토큰 기반 인증 방식. Header.Payload.Signature 3부 구조.

**KRaft** — Kafka Raft. Zookeeper 없이 Kafka 자체적으로 메타데이터를 관리하는 모드.

**MSA (Microservices Architecture)** — 마이크로서비스 아키텍처.

**Outbox 패턴** — 비즈니스 DB 커밋과 Kafka 전송을 원자적으로 만드는 패턴.

**Orchestrator** — 위 "지휘자" 참고.

**Pod** — 위 "포드" 참고.

**RabbitMQ** — AMQP 기반 메시지 브로커. Kafka의 대안.

**REST** — Representational State Transfer. HTTP 기반 API 아키텍처 스타일.

**SockJS** — WebSocket의 폴백 라이브러리.

**SPOF (Single Point of Failure)** — 단일 장애점. 하나가 죽으면 전체가 죽는 지점.

**STOMP** — Simple Text Oriented Messaging Protocol. WebSocket 위의 메시징 프로토콜.

**TCP** — Transmission Control Protocol. WebSocket의 기반.

**TTL (Time-To-Live)** — 데이터의 유효 기간. 만료되면 삭제.

**UUID** — Universally Unique Identifier. 랜덤 문자열 식별자.

**WAL (Write-Ahead Log)** — Kafka의 저장 구조. 파일 끝에 append-only로 기록.

---

**이 책의 마지막 문장**:

이 책에서 다룬 모든 장면은 `chap044/` 폴더의 실제 코드로 구현되어 있다. 책을 덮고, 코드를 열고, 주문을 한 번 내보자. 그리고 로그를 읽어보자. 이 책이 한 줄씩 당신의 머릿속에서 되살아날 것이다.




