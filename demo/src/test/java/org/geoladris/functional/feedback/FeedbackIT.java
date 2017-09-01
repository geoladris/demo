package org.geoladris.functional.feedback;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.geoladris.functional.IntegrationTest;
import org.junit.Test;

public class FeedbackIT extends IntegrationTest {

  @Test
  public void testCommentAndVerify() throws Exception {
    String email = "onuredd@gmail.com";
    String geometry = "POINT(0 1)";
    String comment = "boh";
    String layerName = "classification";
    String layerDate = "1/1/2008";
    CloseableHttpResponse ret = POST("create-comment", "email", email, "geometry", geometry,
        "comment", comment, "layerName", layerName, "date", layerDate);

    // assertEquals(200, ret.getStatusLine().getStatusCode());

    // Get the verification code from the database
    String verificationCode =
        query("SELECT verification_code FROM " + dbSchema + ".redd_feedback ORDER BY date DESC")
            .toString();

    // Check the insert
    assertEquals(email, query("SELECT email FROM " + dbSchema
        + ".redd_feedback WHERE verification_code='" + verificationCode + "'"));
    assertEquals(geometry, query("SELECT ST_AsText(geometry) FROM " + dbSchema
        + ".redd_feedback WHERE verification_code='" + verificationCode + "'"));
    assertEquals(comment, query("SELECT comment FROM " + dbSchema
        + ".redd_feedback WHERE verification_code='" + verificationCode + "'"));
    assertEquals(layerName, query("SELECT layer_name FROM " + dbSchema
        + ".redd_feedback WHERE verification_code='" + verificationCode + "'"));
    assertEquals(layerDate, query("SELECT layer_date FROM " + dbSchema
        + ".redd_feedback WHERE verification_code='" + verificationCode + "'"));

    // Verify it the comment
    ret = GET("verify-comment", "verificationCode", verificationCode);
    assertEquals(200, ret.getStatusLine().getStatusCode());

    // Check cannot validate twice
    ret = GET("verify-comment", "verificationCode", verificationCode);
    assertEquals(404, ret.getStatusLine().getStatusCode());

    // Check validation has not been notified to author
    Long notifiedCount =
        (Long) query("SELECT count(*) FROM " + dbSchema + ".redd_feedback WHERE state=3");
    assertEquals(0, notifiedCount.longValue());

    // Validate the entry and wait (more than the notification delay)
    execute("UPDATE " + dbSchema + ".redd_feedback SET state=2 WHERE verification_code='"
        + verificationCode + "'");
    synchronized (this) {
      wait(6000);
    }

    // Check the entry has been marked as "notified"
    notifiedCount =
        (Long) query("SELECT count(*) FROM " + dbSchema + ".redd_feedback WHERE state=3");
    assertEquals(1, notifiedCount.longValue());
  }

  @Test
  public void testCommentWrongEmail() throws Exception {
    CloseableHttpResponse ret = POST("create-comment", "email", "wrongaddress", "geometry",
        "POINT(0 1)", "comment", "boh", "layerName", "classification", "date", "1/1/2008");

    System.out.println(IOUtils.toString(ret.getEntity().getContent()));

    assertEquals(500, ret.getStatusLine().getStatusCode());
  }

  @Test
  public void testCommentWrongWKT() throws Exception {
    CloseableHttpResponse ret = POST("create-comment", "email", "onuredd@gmail.com", "geometry",
        "POINT(0, 1)", "comment", "boh", "layerName", "classification", "date", "1/1/2008");

    System.out.println(IOUtils.toString(ret.getEntity().getContent()));

    assertEquals(500, ret.getStatusLine().getStatusCode());
  }

  @Test
  public void testVerifyI18n() throws Exception {
    CloseableHttpResponse en = GET("verify-comment", "verificationCode", "1", "lang", "en");
    CloseableHttpResponse es = GET("verify-comment", "verificationCode", "1", "lang", "es");
    assertFalse(en.equals(es));
  }

  @Test
  public void testMissingParameter() throws Exception {
    CloseableHttpResponse ret = POST("create-comment", "email", "onuredd@gmail.com", "geometry",
        "POINT(0, 1)", "layerName", "classification", "date", "1/1/2008");

    System.out.println(IOUtils.toString(ret.getEntity().getContent()));

    assertEquals(400, ret.getStatusLine().getStatusCode());
  }
}
