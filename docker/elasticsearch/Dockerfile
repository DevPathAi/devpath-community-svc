# 커뮤니티 검색용 Elasticsearch + 한국어 형태소 분석기(nori).
# 공식 이미지에 nori 가 없고 GitHub Actions services: 는 컨테이너 내부에서
# 임의 명령을 실행할 수 없어, 플러그인을 미리 설치한 이미지를 CI 와 배포가 공유한다.
FROM docker.elastic.co/elasticsearch/elasticsearch:9.2.8
RUN elasticsearch-plugin install --batch analysis-nori
