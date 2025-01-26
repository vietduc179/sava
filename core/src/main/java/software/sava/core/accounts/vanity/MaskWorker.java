package software.sava.core.accounts.vanity;

import software.sava.core.encoding.Base58;

import java.security.SecureRandom;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class MaskWorker extends BaseMaskWorker {

  private final Subsequence endsWith;

  MaskWorker(final SecureRandom secureRandom,
             final Subsequence beginsWith,
             final Subsequence endsWith,
             final long find,
             final AtomicInteger found,
             final AtomicLong searched,
             final Queue<Result> results,
             final int checkFound) {
    super(secureRandom, beginsWith, find, found, searched, results, checkFound);
    this.endsWith = endsWith;
  }

  @Override
  public void run() {
    final int endLength = endsWith.length();
    final char[] shortEncoded = new char[endLength << 1];
    final int shortStart = shortEncoded.length - endLength;
    long fastEncodeOffsets;
    long start = System.currentTimeMillis();
    for (int i = 0, keyStart, shortKeyStart; ; ) {
      generateKeyPair();

      fastEncodeOffsets = Base58.beginMutableEncode(mutablePublicKey, endLength, shortEncoded);
      shortKeyStart = (int) fastEncodeOffsets;
      if (endsWith.contains(shortEncoded, shortStart)) {
        final int shortEncodedLen = shortEncoded.length - shortKeyStart;
        final int encodedStart = encoded.length - shortEncodedLen;

        keyStart = Base58.continueMutableEncode(
            mutablePublicKey,
            (int) (fastEncodeOffsets >>> 48),
            (int) (fastEncodeOffsets >>> 32) & 0xFFFF,
            encodedStart,
            encoded
        );
        if (queueResult(start, keyStart)) {
          if (incrementFoundHitsLimitOrInterrupted()) {
            return;
          } else {
            searched.getAndAccumulate(i, SUM);
            i = 0;
            start = System.currentTimeMillis();
            continue;
          }
        }
      }
      if (++i == checkFound) {
        if (foundLimitOrInterrupted()) {
          return;
        } else {
          i = 0;
        }
      }
    }
  }
}
