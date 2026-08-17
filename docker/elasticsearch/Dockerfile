# 커뮤니티 검색용 Elasticsearch + 한국어 형태소 분석기(nori).
# 공식 이미지에 nori 가 없어 플러그인을 미리 설치한 이미지가 필요하다.
# CI 는 이 Dockerfile 을 매 실행마다 직접 빌드해 쓰고(레지스트리 권한에 의존하지 않기
# 위해서다 — .github/workflows/ci.yml 의 "Start Elasticsearch" step 주석 참조),
# 로컬·k3s 배포도 같은 정의를 공유한다.
FROM docker.elastic.co/elasticsearch/elasticsearch:9.2.8@sha256:f6c331bc14f54c32ce956d0f35cf17a2d302214bdf89b7234e5c9ada09602357
RUN elasticsearch-plugin install --batch analysis-nori
