/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.vm;

import static org.hyperledger.besu.evm.operation.BlockHashOperation.BlockHashLookup;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachingBlockHashLookupTest {

  @Mock private Blockchain blockchain;
  @Mock private MessageFrame messageFrame;
  @Mock private BlockValues blockValues;

  private static final int CURRENT_BLOCK_NUMBER = 300;
  private final BlockHeader[] headers = new BlockHeader[CURRENT_BLOCK_NUMBER];
  private BlockHashLookup lookup;

  @BeforeEach
  void setUp() {
    BlockHeader parentHeader = null;
    for (int i = 0; i < headers.length; i++) {
      final BlockHeader header = createHeader(i, parentHeader);
      lenient().when(blockchain.getBlockHeader(header.getHash())).thenReturn(Optional.of(header));
      headers[i] = header;
      parentHeader = headers[i];
    }
    when(messageFrame.getBlockValues()).thenReturn(blockValues);
    when(blockValues.getNumber()).thenReturn((long) CURRENT_BLOCK_NUMBER);

    lookup =
        new CachingBlockHashLookup(
            createHeader(CURRENT_BLOCK_NUMBER, headers[headers.length - 1]), blockchain);
  }

  @AfterEach
  void verifyBlocksNeverLookedUpByNumber() {
    // Looking up the block by number is incorrect because it always uses the canonical chain even
    // if the block being imported is on a fork.
    verify(blockchain, never()).getBlockHeader(anyLong());
  }

  @Test
  void shouldGetHashOfImmediateParent() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  void shouldGetHashForRecentBlockAfterOlderBlock() {
    assertHashForBlockNumber(100);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
  }

  @Test
  void shouldReturnEmptyHashWhenRequestedGenesis() {
    Assertions.assertThat(lookup.apply(messageFrame, 0L)).isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldReturnEmptyHashWhenRequestedTooFarBack() {
    Assertions.assertThat(lookup.apply(messageFrame, CURRENT_BLOCK_NUMBER - 260L))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldReturnEmptyHashWhenRequestedCurrentBlock() {
    Assertions.assertThat(lookup.apply(messageFrame, (long) CURRENT_BLOCK_NUMBER))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldReturnEmptyHashWhenRequestedBlockNotOnchain() {
    Assertions.assertThat(lookup.apply(messageFrame, CURRENT_BLOCK_NUMBER + 20L))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldReturnEmptyHashWhenParentBlockNotOnchain() {
    final BlockHashLookup lookupWithUnavailableParent =
        new CachingBlockHashLookup(
            new BlockHeaderTestFixture().number(CURRENT_BLOCK_NUMBER + 20).buildHeader(),
            blockchain);
    Assertions.assertThat(
            lookupWithUnavailableParent.apply(messageFrame, (long) CURRENT_BLOCK_NUMBER))
        .isEqualTo(Hash.ZERO);
  }

  @Test
  void shouldGetParentHashFromCurrentBlock() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
    verifyNoInteractions(blockchain);
  }

  @Test
  void shouldCacheBlockHashesWhileIteratingBackToPreviousHeader() {
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 4);
    assertHashForBlockNumber(CURRENT_BLOCK_NUMBER - 1);
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 1].getHash());
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 2].getHash());
    verify(blockchain).getBlockHeader(headers[CURRENT_BLOCK_NUMBER - 3].getHash());
    verifyNoMoreInteractions(blockchain);
  }

  private void assertHashForBlockNumber(final int blockNumber) {
    Assertions.assertThat(lookup.apply(messageFrame, (long) blockNumber))
        .isEqualTo(headers[blockNumber].getHash());
  }

  private BlockHeader createHeader(final int blockNumber, final BlockHeader parentHeader) {
    return new BlockHeaderTestFixture()
        .number(blockNumber)
        .parentHash(parentHeader != null ? parentHeader.getHash() : Hash.EMPTY)
        .buildHeader();
  }
}
