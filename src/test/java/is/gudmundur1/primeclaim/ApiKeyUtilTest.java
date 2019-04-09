package is.gudmundur1.primeclaim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class ApiKeyUtilTest {

  @Test
  void generate() {
    String generated = ApiKeyUtil.generateApiKey();
    assertEquals(16, generated.length());
    assertTrue(generated.matches("[A-Z0-9]+"));
  }

}
