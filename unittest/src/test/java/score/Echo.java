package score;

import java.lang.String;
import score.annotation.External;

public class Echo {

  @External(readonly=true)
  public String echo(String message) {
    return message;
  }
}
