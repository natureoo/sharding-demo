package com.t20worldcup.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.t20worldcup.states.EHKDType;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.Random;

public interface RedeemEHKDFlows {

    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
        private String accountName;
//        private String currency;
        private Long amount;

        private final Party issuer;

        private final Logger logger = LoggerFactory.getLogger(RedeemEHKDFlows.class);

        @NotNull
        private final ProgressTracker progressTracker;

        private final static ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on parameters.");
        private final static ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final static ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final static ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION);
        }

        /**
         * This constructor would typically be called by RPC or by {@link FlowLogic#subFlow}.
         */
//        public Initiator(String accountName, String currency, Long amount, String notaryName)  {
//            this.accountName = accountName;
//            this.currency = currency;
//            this.amount = amount;
//            this.notaryName = notaryName;
//            this.progressTracker = tracker();
//        }

        public Initiator(String accountName, Long amount, Party issuer)  {
            this.accountName = accountName;
            this.amount = amount;
            this.issuer = issuer;
            this.progressTracker = tracker();
        }



        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            AccountInfo accountInfo = UtilitiesKt.getAccountService(this).accountInfo(accountName).get(0).getState().getData();

            //To transact with any account, we have to request for a Key from the node hosting the account. For this we use RequestKeyForAccount inbuilt flow.
            //This will return a Public key wrapped in an AnonymousParty class.
            AnonymousParty anonymousParty = subFlow(new RequestKeyForAccount(accountInfo));

            final TokenType tokenType = new EHKDType();

            // Describe how to find those $ held by Me.
            final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(tokenType, anonymousParty);
            final Amount<TokenType> ehkDAmount = AmountUtilitiesKt.amount(amount, tokenType);

            // Do the redeem
            return subFlow(new RedeemFungibleTokens(
                    ehkDAmount, // How much to redeem
                    issuer, // issuer
                    Collections.emptyList(), // Observers
                    heldByMe, // Criteria to find the inputs
                    anonymousParty)); // change holder

        }


    }


    @InitiatedBy(Initiator.class)
    class Responder extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession counterpartySession;

        public Responder(@NotNull final FlowSession counterpartySession) {
            this.counterpartySession = counterpartySession;
        }


        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(counterpartySession));
        }
    }

}
