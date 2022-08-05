package com.t20worldcup.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensUtilitiesKt;
import com.t20worldcup.states.MealTicketType;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Currency;
import java.util.Random;

public interface IssueMealTicketFlows {

    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
        private String accountName;
//        private String currency;
        private Long quantity; //

//        private String notaryName;

        private final Logger logger = LoggerFactory.getLogger(IssueMealTicketFlows.class);


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

        public Initiator(String accountName, Long quantity)  {
            this.accountName = accountName;
            this.quantity = quantity;
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

            //Dealer node has already shared accountinfo with the bank when we ran the CreateAndShareAccountFlow. So this bank node will
            //have access to AccountInfo of the buyer. Retrieve it using the AccountService. AccountService has certain helper methods, take a look at them.
            AccountInfo accountInfo = UtilitiesKt.getAccountService(this).accountInfo(accountName).get(0).getState().getData();

            //To transact with any account, we have to request for a Key from the node hosting the account. For this we use RequestKeyForAccount inbuilt flow.
            //This will return a Public key wrapped in an AnonymousParty class.
            AnonymousParty anonymousParty = subFlow(new RequestKeyForAccount(accountInfo));

            //Get the base token type for issuing fungible tokens
            final MealTicketType token = new MealTicketType();

            //issuer will be the bank. Keep in mind the issuer will always be an known legal Party class and not an AnonymousParty. This is by design
            IssuedTokenType issuedTokenType = new IssuedTokenType(getOurIdentity(), token);

            //Create a fungible token for issuing cash to account
            FungibleToken fungibleToken = new FungibleToken(new Amount(this.quantity, issuedTokenType), anonymousParty, MealTicketType.getContractAttachment());

//            final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse(notaryName)); // METHOD 1
//            Random random = new Random();
//            int index = random.nextInt(getServiceHub().getNetworkMapCache().getNotaryIdentities().size());
//            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0); // METHOD 1
            // final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")); // METHOD 2
            Random random = new Random();
            int index = random.nextInt(getServiceHub().getNetworkMapCache().getNotaryIdentities().size());
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(index); // METHOD 1
            logger.info("IssueMealTicketFlows select notary[{}]", notary);

            //create a transactionBuilder
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            IssueTokensUtilitiesKt.addIssueTokens(transactionBuilder, fungibleToken);

            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder,
                    Arrays.asList(getOurIdentity().getOwningKey()));
            FlowSession holderSession = initiateFlow(accountInfo.getHost());

            //Note: though buyer and seller are on the same node still we will have to call CollectSignaturesFlow as the signer is not a Party but an account.
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction,
                    Arrays.asList(holderSession)));

            return subFlow(new FinalityFlow(fullySignedTx, Arrays.asList(holderSession)));

        }

        public TokenType getInstance(String currencyCode) {
            Currency currency = Currency.getInstance(currencyCode);
            return new TokenType(currency.getCurrencyCode(), 0);
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
