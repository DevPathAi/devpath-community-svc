package ai.devpath.community.post.dto;

/** 글·질문 수정. 태그는 바꾸지 않는다 — 평판이 투표 시점 태그로 귀속되어 소급 변경이 어긋난다. */
public record UpdatePostRequest(String title, String bodyMd) {}
