/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.btp;


import foundation.icon.jsonrpc.Address;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import foundation.icon.jsonrpc.model.TransactionResult;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;

import network.balanced.solidity.btp.CallService;
import network.balanced.solidity.btp.MockBMC;
import network.balanced.score.btp.icon.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Relay {
    public static Consumer<TransactionResult> relayToEVM(ReqID reqID) {
        return sendMessageEvent((el) -> {
             var checker = callMessageEvent_EVM((el2) -> {
                reqID.id = el2._reqId;
            });
            assertDoesNotThrow(() ->checker.accept((MockBTP.BMC_EVM.handleBTPMessage(MockBTP.XCall_EVM.getContractAddress(), "icon", el.getSvc(), el.getSn(), el.getMsg()).send())));
        });
    }

    public static Consumer<TransactionReceipt> relayToICON(ReqID reqID) {
        return sendMessageEvent_EVM((el) -> {
            MockBTP.BMC_ICON.handleBTPMessage(callMessageEvent((el2) -> {
                    reqID.id = el2.getReqId();
                }
                ), MockBTP.XCall_ICON._address(), "evm", el._svc, el._sn, el._msg);
        });
    }

    public static Consumer<TransactionResult> relayRollbackToEVM(ReqID reqID) {
        return sendMessageEvent((el) -> {
             var checker = rollbackMessageEvent_EVM((el2) -> {
                reqID.sn = el2._sn;
            });
            assertDoesNotThrow(() ->checker.accept((MockBTP.BMC_EVM.handleBTPMessage(MockBTP.XCall_EVM.getContractAddress(), "icon", el.getSvc(), el.getSn(), el.getMsg()).send())));
        });
    }

    public static Consumer<TransactionReceipt> relayRollbackToICON(ReqID reqID) {
        return sendMessageEvent_EVM((el) -> {
            MockBTP.BMC_ICON.handleBTPMessage(rollbackMessageEvent((el2) -> {
                    reqID.sn = el2.getSn();
                }
                ), MockBTP.XCall_ICON._address(), "evm", el._svc, el._sn, el._msg);
        });
    }

    public static void executeXCallICON(ReqID reqID) {
        MockBTP.XCall_ICON.executeCall(reqID.id);
    }

    public static void executeXCallEVM(ReqID reqID) {
        assertDoesNotThrow(() -> MockBTP.XCall_EVM.executeCall(reqID.id).send());
    }

    public static void executeRollbackICON(ReqID reqID) {
        MockBTP.XCall_ICON.executeRollback(reqID.sn);
    }

    public static void executeRollbackEVM(ReqID reqID) {
        assertDoesNotThrow(() -> MockBTP.XCall_EVM.executeRollback(reqID.sn).send());
    }

    public static void executeAndRelayResponseICON(ReqID reqID) {
        MockBTP.XCall_ICON.executeCall(relayRollbackToEVM(reqID), reqID.id);
    }

    public static void executeAndRelayResponseEVM(ReqID reqID) {
        TransactionReceipt t = assertDoesNotThrow(() -> MockBTP.XCall_EVM.executeCall(reqID.id).send());
        relayRollbackToICON(reqID).accept(t);
    }

    public static Consumer<TransactionResult> sendMessageEvent(
            Consumer<SendMessageEventLog> consumer) {
        return eventLogChecker(
                MockBTP.BMC_ICON._address(),
                SendMessageEventLog::eventLogs,
                consumer);
    }

    public static Consumer<TransactionResult> callMessageEvent(
            Consumer<CallMessageEventLog> consumer) {
        return eventLogChecker(
                MockBTP.XCall_ICON._address(),
                CallMessageEventLog::eventLogs,
                consumer);
    }

    static Consumer<TransactionResult> rollbackMessageEvent(
        Consumer<RollbackMessageEventLog> consumer) {
    return eventLogChecker(
            MockBTP.XCall_ICON._address(),
            RollbackMessageEventLog::eventLogs,
            consumer);
    }

    static Consumer<TransactionReceipt> rollbackMessageEvent_EVM(
        Consumer<CallService.RollbackMessageEventResponse> consumer) {
        return eventLogChecker(
            MockBTP.XCall_EVM.getContractAddress(),
            CallService::getRollbackMessageEvents,
            consumer);
    }


    public static Consumer<TransactionReceipt> callMessageEvent_EVM(
            Consumer<CallService.CallMessageEventResponse> consumer) {
        return eventLogChecker(
                MockBTP.XCall_EVM.getContractAddress(),
                CallService::getCallMessageEvents,
                consumer);
        }

    public static Consumer<TransactionReceipt> sendMessageEvent_EVM(
            Consumer<MockBMC.SendMessageEventResponse> consumer) {
        return eventLogChecker(
                MockBTP.BMC_EVM.getContractAddress(),
                MockBMC::getSendMessageEvents,
                consumer);
    }

    @FunctionalInterface
    interface EventLogsSupplier<T extends BaseEventResponse> {
        List<T> apply(TransactionReceipt txr);
    }

    static <T extends BaseEventResponse> Consumer<TransactionReceipt> eventLogChecker(
            String address,
            EventLogsSupplier<T> supplier,
            Consumer<T> consumer) {
        return (txr) -> {
            List<T> eventLogs = supplier.apply(txr).stream()
                    .filter((el) -> el.log.getAddress().equals(address))
                    .collect(Collectors.toList());
            assertEquals(1, eventLogs.size());
            if (consumer != null) {
                consumer.accept(eventLogs.get(0));
            }
        };
    }

    private static <T> Consumer<TransactionResult> eventLogChecker(
            Address address,
            ScoreIntegrationTest.EventLogsSupplier<T> supplier,
            Consumer<T> consumer) {
        return ScoreIntegrationTest.eventLogChecker(
            address, supplier, consumer);
    }
}
