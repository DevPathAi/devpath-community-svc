package ai.devpath.community.post;

import ai.devpath.shared.error.ApiException;
import ai.devpath.shared.error.ErrorCode;

/** 잘못된 투표값 → 스펙 §3.4 VALIDATION_FAILED(400). 공용 ApiExceptionHandler가 envelope로 렌더. */
public class InvalidVoteException extends ApiException {
  public InvalidVoteException(String msg) {
    super(ErrorCode.VALIDATION_FAILED, msg);
  }
}
