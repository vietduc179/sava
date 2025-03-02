package software.sava.core.accounts.lookup;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static software.sava.core.accounts.PublicKey.readPubKey;

final class AddressLookupTableOverlay extends AddressLookupTableRoot {

  AddressLookupTableOverlay(final PublicKey address, final byte[] data) {
    super(address, data);
  }

  @Override
  public byte[] discriminator() {
    final byte[] discriminator = new byte[4];
    System.arraycopy(data, 0, discriminator, 0, 4);
    return discriminator;
  }

  @Override
  public long deactivationSlot() {
    return ByteUtil.getInt64LE(data, DEACTIVATION_SLOT_OFFSET);
  }

  @Override
  public long lastExtendedSlot() {
    return ByteUtil.getInt64LE(data, LAST_EXTENDED_OFFSET);
  }

  @Override
  public int lastExtendedSlotStartIndex() {
    return data[LAST_EXTENDED_SLOT_START_INDEX_OFFSET] & 0xFF;
  }

  @Override
  public PublicKey authority() {
    return data[AUTHORITY_OPTION_OFFSET] == 0
        ? null
        : readPubKey(data, AUTHORITY_OFFSET);
  }

  @Override
  public AddressLookupTable withReverseLookup() {
    final int numAccounts = numAccounts();
    final var accounts = new PublicKey[numAccounts];
    final var distinctAccounts = new HashMap<PublicKey, Integer>(numAccounts);
    for (int i = 0, from = LOOKUP_TABLE_META_SIZE, to = data.length; from < to; ++i, from += PUBLIC_KEY_LENGTH) {
      final var pubKey = readPubKey(data, from);
      distinctAccounts.putIfAbsent(pubKey, i);
      accounts[i] = pubKey;
    }
    return new AddressLookupTableWithReverseLookup(
        address,
        discriminator(),
        deactivationSlot(),
        lastExtendedSlot(),
        lastExtendedSlotStartIndex(),
        authority(),
        distinctAccounts,
        accounts,
        data
    );
  }

  @Override
  public Set<PublicKey> uniqueAccounts() {
    final int numAccounts = numAccounts();
    final var distinctAccounts = new HashSet<PublicKey>(numAccounts);
    for (int i = 0, from = LOOKUP_TABLE_META_SIZE, to = data.length; from < to; ++i, from += PUBLIC_KEY_LENGTH) {
      distinctAccounts.add(readPubKey(data, from));
    }
    return distinctAccounts;
  }

  @Override
  public int numUniqueAccounts() {
    return uniqueAccounts().size();
  }

  @Override
  public PublicKey account(final int index) {
    return readPubKey(data, LOOKUP_TABLE_META_SIZE + (index << 5));
  }

  @Override
  public int indexOf(final PublicKey publicKey) {
    final byte[] bytes = publicKey.toByteArray();
    for (int from = LOOKUP_TABLE_META_SIZE, to = from + PUBLIC_KEY_LENGTH, numAccounts = numAccounts(), i = 0; i < numAccounts; ++i) {
      if (Arrays.equals(
          bytes, 0, PUBLIC_KEY_LENGTH,
          data, from, to)) {
        return i;
      }
      from = to;
      to += PUBLIC_KEY_LENGTH;
    }
    return Integer.MIN_VALUE;
  }

  @Override
  public byte indexOfOrThrow(final PublicKey publicKey) {
    final int index = indexOf(publicKey);
    if (index < 0) {
      throw new IllegalStateException(String.format("Could not find %s in lookup table.", publicKey.toBase58()));
    } else {
      return (byte) index;
    }
  }

  @Override
  public int numAccounts() {
    return (data.length - LOOKUP_TABLE_META_SIZE) >> 5;
  }

  @Override
  protected String keysToString() {
    final int to = data.length;
    return IntStream.iterate(LOOKUP_TABLE_META_SIZE, i -> i < to, i -> i + PUBLIC_KEY_LENGTH)
        .mapToObj(i -> readPubKey(data, i))
        .map(PublicKey::toBase58)
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public boolean equals(final Object obj) {
    return obj instanceof AddressLookupTableOverlay overlay
        && address.equals(overlay.address)
        && Arrays.equals(data, 0, data.length, overlay.data, 0, overlay.data.length);
  }

  @Override
  public int hashCode() {
    return address.hashCode();
  }
}
