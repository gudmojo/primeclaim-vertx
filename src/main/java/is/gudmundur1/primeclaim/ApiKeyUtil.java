package is.gudmundur1.primeclaim;

import java.security.SecureRandom;
import java.util.Random;

public class ApiKeyUtil {
  public static String generateApiKey() {
    StringBuilder stringBuilder = new StringBuilder();
    Random random = new SecureRandom();
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    for (int i=0; i<16; i++) {
      int point = random.nextInt(alphabet.length());
      stringBuilder.append(alphabet.charAt(point));
    }
    return stringBuilder.toString();
  }

}
