package com.t20worldcup.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.t20worldcup.states.EHKDType;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
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

public interface MoveEHKDFlows {

    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
        private String fromAccountName;

        private String toAccountName;
//        private String currency;
        private Long amount;

//        private String notaryName;

        private final Logger logger = LoggerFactory.getLogger(MoveEHKDFlows.class);

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
        public Initiator(String fromAccountName, String toAccountName, Long amount)  {
            this.fromAccountName = fromAccountName;
            this.toAccountName = toAccountName;
            this.amount = amount;
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
            final EHKDType ehkdTokenType = new EHKDType();

            Amount<FiatCurrency> ehkdAmount = new Amount(amount, ehkdTokenType);
            //Get buyers and sellers account infos
            AccountInfo fromAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(fromAccountName).get(0).getState().getData();
            AccountInfo toAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(toAccountName).get(0).getState().getData();

            //Generate new keys for buyers and sellers
            AnonymousParty fromAccount = subFlow(new RequestKeyForAccount(fromAccountInfo));
            AnonymousParty toAccount = subFlow(new RequestKeyForAccount(toAccountInfo));

            //move money to sellerAccountInfo account.
            PartyAndAmount ehkdPartyAndAmount = new PartyAndAmount(toAccount, ehkdAmount);

            TransactionBuilder transactionBuilder = new TransactionBuilder();

            //construct the query criteria and get all available unconsumed fungible tokens which belong to buyers account
            QueryCriteria ehkdCriteria = new QueryCriteria.VaultQueryCriteria().withStatus(Vault.StateStatus.UNCONSUMED)
                    .withExternalIds(Arrays.asList(fromAccountInfo.getIdentifier().getId()));
//                    .withNotary(Arrays.asList(notary));

            //call utility function to move the fungible token from buyer to seller account
            //this also adds inputs and outputs to the transactionBuilder
            //till now we have only 1 transaction with 2 inputs and 2 outputs - one moving fungible tokens other moving non fungible tokens between accounts
            MoveTokensUtilitiesKt.addMoveFungibleTokens(transactionBuilder, getServiceHub(), Arrays.asList(ehkdPartyAndAmount), fromAccount, ehkdCriteria);

            //self sign the transaction. note : the host party will first self sign the transaction.
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder,
                    Arrays.asList(getOurIdentity().getOwningKey()));

            //establish sessions with buyer and seller. to establish session get the host name from accountinfo object
            FlowSession fromAccountSession = initiateFlow(fromAccountInfo.getHost());

            FlowSession toAccountSession = initiateFlow(toAccountInfo.getHost());

            //Note: though buyer and seller are on the same node still we will have to call CollectSignaturesFlow as the signer is not a Party but an account.
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction,
                    Arrays.asList(fromAccountSession, toAccountSession)));

            //call ObserverAwareFinalityFlow for finality
            SignedTransaction stx = subFlow(new ObserverAwareFinalityFlow(fullySignedTx, Arrays.asList(fromAccountSession, toAccountSession)));


            return subFlow(new FinalityFlow(fullySignedTx, Arrays.asList(fromAccountSession, toAccountSession)));

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
