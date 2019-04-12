package is.gudmundur1.primeclaim;

import java.util.stream.IntStream;

public class PrimeUtil {
  public static boolean isPrime(Integer candidate) {
    if (candidate == null) {
      return false;
    }
    int candidateRoot = (int) Math.sqrt( (double) candidate);
    return IntStream.range(2 ,candidateRoot)
      .boxed().noneMatch(x -> candidate % x == 0);
  }
}
