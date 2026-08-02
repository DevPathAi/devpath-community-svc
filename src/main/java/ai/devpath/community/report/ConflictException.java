package ai.devpath.community.report;

import ai.devpath.shared.error.ApiException;
import ai.devpath.shared.error.ErrorCode;

/** 중복 신고·재처리 등 상태 충돌 → 스펙 §3.4 CONFLICT(409). 공용 ApiExceptionHandler가 envelope로 렌더. */
public class ConflictException extends ApiException {
  public ConflictException(String msg) {
    super(ErrorCode.CONFLICT, msg);
  }
}
