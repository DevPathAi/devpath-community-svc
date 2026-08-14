package ai.devpath.community.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.devpath.community.report.dto.AdminReportResponse;
import ai.devpath.community.report.dto.AdminReportView;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 신고 API 의 권한 계약.
 *
 * <p>{@code jwt()} 후처리기 대신 <b>실제 서명 JWT</b>를 보낸다. 후처리기는 authority 를 직접
 * 주입해 {@code SecurityConfig} 의 {@code role} → {@code ROLE_*} 변환기를 건너뛰므로,
 * "role=ADMIN 토큰이 실제로 통과하는가"라는 핵심 질문에 답하지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminReportControllerTest {

  @Value("${devpath.auth.jwt-secret}") String secret;

  @Autowired MockMvc mvc;
  @MockitoBean ReportService service;

  @Test
  void listReturnsEnvelopeForAdmin() throws Exception {
    AdminReportView v = new AdminReportView(5L, "POST", 1L, "제목", "발췌", 7L,
        "/community/1", 3L, "SPAM", "광고", 2L, "OPEN", "2026-08-02T00:00:00Z");
    when(service.list(any(), anyInt(), anyInt()))
        .thenReturn(new AdminReportResponse(List.of(v), 1, 0, 20));

    mvc.perform(get("/community/admin/reports?status=OPEN")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].targetTitle").value("제목"))
        .andExpect(jsonPath("$.items[0].targetPath").value("/community/1"))
        .andExpect(jsonPath("$.items[0].reportCount").value(2))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void listIsForbiddenForNonAdmin() throws Exception {
    mvc.perform(get("/community/admin/reports")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("LEARNER")))
        .andExpect(status().isForbidden());

    verify(service, never()).list(any(), anyInt(), anyInt());
  }

  @Test
  void listIsUnauthorizedWithoutToken() throws Exception {
    mvc.perform(get("/community/admin/reports")).andExpect(status().isUnauthorized());
  }

  @Test
  void resolveReturnsUpdatedStatus() throws Exception {
    CommunityReport r = new CommunityReport();
    r.setStatus("RESOLVED");
    when(service.resolve(anyLong(), anyLong(), any())).thenReturn(r);

    mvc.perform(post("/community/admin/reports/5/resolve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"RESOLVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }

  @Test
  void resolveIsForbiddenForNonAdmin() throws Exception {
    mvc.perform(post("/community/admin/reports/5/resolve")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithRole("LEARNER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"RESOLVE\"}"))
        .andExpect(status().isForbidden());

    verify(service, never()).resolve(anyLong(), anyLong(), any());
  }

  /** HS256 서명 토큰. role 이 null 이면 클레임 자체를 넣지 않는다. */
  private String tokenWithRole(String role) throws Exception {
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
        .subject("1")
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 60_000));
    if (role != null) {
      claims.claim("role", role);
    }
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
    jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}
