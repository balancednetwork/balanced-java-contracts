package network.balanced.score.core;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import com.eclipsesource.json.Json;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import score.Context;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import network.balanced.score.mock.DexMock;
import network.balanced.score.core.RewardsMock;
import network.balanced.score.core.StakingMock;
import network.balanced.score.core.ReserveMock;

import network.balanced.score.core.sICXMintBurn;
import network.balanced.score.core.bnUSDMintBurn;
import network.balanced.score.core.Loans;
import network.balanced.score.core.Constants;

import java.util.Random;

@DisplayName("Loans Tests")
class LoansTestContinuousRewards extends LoansTestsBase {

    static final String continuousRewardsErrorMessage = "The continuous rewards is already active.";   


    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
        enableContinuousRewards();
    }

    // @Test
    // void getPositonStanding() {
    //     Account account = accounts.get(0);
    //     int lastDay = -1;
    //     Executable getPositionStanding = () -> loans.call("getPositionStanding", account.getAddress(), lastDay);
    //     expectErrorMessage(getPositionStanding, continuousRewardsErrorMessage);
    // }


    // @Test
    // void getPositionByIndex() {
    //     int index = 1;
    //     int lastDay = -1;
    //     Executable getPositionByIndex = () -> loans.call("getPositionByIndex", index, lastDay);
    //     expectErrorMessage(getPositionByIndex, continuousRewardsErrorMessage);
    // }

    // @Test
    // void getTotalValue() {
    //     int lastDay = -1;
    //     Executable getTotalValue = () -> loans.call("getTotalValue", "loans", lastDay);
    //     expectErrorMessage(getTotalValue, continuousRewardsErrorMessage);
    // }

    @Test
    void getDataBatch_SameDay() {
        int lastDay = -1;
        //Does not throw error
        loans.call("getDataBatch", "loans", lastDay, 0, 0);
    }

    @Test
    void getDataBatch_DayAfter() {
        int day = (int)loans.call("getDay") + 1;

        Executable getDataBatch = () -> loans.call("getDataBatch", "loans", day, 0, 0);
        expectErrorMessage(getDataBatch, continuousRewardsErrorMessage);
    }


    


}