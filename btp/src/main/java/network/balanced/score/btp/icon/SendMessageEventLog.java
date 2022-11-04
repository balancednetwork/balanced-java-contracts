/*
 * Copyright 2021 ICON Foundation
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

package network.balanced.score.btp.icon;

import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.IconJsonModule;
import foundation.icon.jsonrpc.model.TransactionResult;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;

public class SendMessageEventLog {
    static final String SIGNATURE = "SendMessage(int,str,str,int,bytes)";
    private BigInteger nsn;
    private String to;
    private String svc;
    private BigInteger sn;
    private byte[] msg;

    public SendMessageEventLog(TransactionResult.EventLog el) {
        this.nsn = IconJsonModule.NumberDeserializer.BIG_INTEGER.convert(
                el.getIndexed().get(1));
        this.to = el.getData().get(0);
        this.svc = el.getData().get(1);
        this.sn = IconJsonModule.NumberDeserializer.BIG_INTEGER.convert(
                el.getData().get(2));
        this.msg = IconJsonModule.ByteArrayDeserializer.BYTE_ARRAY.convert(
                el.getData().get(3));
    }

    public BigInteger getNsn() {
        return nsn;
    }

    public String getTo() {
        return to;
    }

    public String getSvc() {
        return svc;
    }

    public BigInteger getSn() {
        return sn;
    }

    public byte[] getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SendMessageEventLog{");
        sb.append("nsn=").append(nsn);
        sb.append(", to='").append(to).append('\'');
        sb.append(", svc='").append(svc).append('\'');
        sb.append(", sn=").append(sn);
        sb.append(", msg=").append(StringUtil.toString(msg));
        sb.append('}');
        return sb.toString();
    }

    public static List<SendMessageEventLog> eventLogs(
            TransactionResult txr, Address address, Predicate<SendMessageEventLog> filter) {
        return ScoreIntegrationTest.eventLogs(txr,
                SendMessageEventLog.SIGNATURE,
                address,
                SendMessageEventLog::new,
                filter);
    }

}
