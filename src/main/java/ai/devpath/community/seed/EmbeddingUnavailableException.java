package ai.devpath.community.seed;

public class EmbeddingUnavailableException extends RuntimeException {
  public EmbeddingUnavailableException(String msg) { super(msg); }
  public EmbeddingUnavailableException(String msg, Throwable cause) { super(msg, cause); }
}
