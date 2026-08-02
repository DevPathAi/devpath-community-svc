package ai.devpath.community.report;

import ai.devpath.shared.error.ApiException;
import ai.devpath.shared.error.ErrorCode;

/** 잘못된 신고 요청(본인 콘텐츠·enum 밖·사유 길이 초과) → 스펙 §3.4 VALIDATION_FAILED(400). */
public class InvalidReportException extends ApiException {
  public InvalidReportException(String msg) {
    super(ErrorCode.VALIDATION_FAILED, msg);
  }
}
