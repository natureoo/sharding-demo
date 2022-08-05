package com.t20worldcup.flows;


import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.t20worldcup.states.EHKDType;
import com.t20worldcup.states.MealTicketType;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Random;

/**
 * This is the DVP flow, where the buyer account buys the ticket token from the dealer account and in turn transfers him cash worth of the ticket.
 * Once buyer1 buys the token from the dealer, he can further sell this ticket to other buyers.
 * Note : this flow handles dvp from account to account on same node. This flow later will be modified if a buyer on dealer1 node wants to buy ticket from
 * dealer2 node.
 */
@StartableByRPC
@InitiatingFlow
public class DVPAccountsOnSameNode extends FlowLogic<String> {

    private Long quantityOfTicket;
    private final String buyerAccountName;
    private final String sellerAccountName;
    private final Long costOfTicket;
    private final Logger logger = LoggerFactory.getLogger(DVPAccountsOnSameNode.class);


    public DVPAccountsOnSameNode(Long quantityOfTicket, String buyerAccountName, String sellerAccountName, Long costOfTicket) {
        this.quantityOfTicket = quantityOfTicket;
        this.buyerAccountName = buyerAccountName;
        this.sellerAccountName = sellerAccountName;
        this.costOfTicket = costOfTicket;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {

        //Get buyers and sellers account infos
        AccountInfo buyerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(buyerAccountName).get(0).getState().getData();
        AccountInfo sellerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(sellerAccountName).get(0).getState().getData();

        //Generate new keys for buyers and sellers
        AnonymousParty buyerAccount = subFlow(new RequestKeyForAccount(buyerAccountInfo));
        AnonymousParty sellerAccount = subFlow(new RequestKeyForAccount(sellerAccountInfo));

        // Obtain a reference to a notary we wish to use.
        /** METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
         *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flow or parsed from config (Preferred)
         *
         *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
         */
        Random random = new Random();
        int index = random.nextInt(getServiceHub().getNetworkMapCache().getNotaryIdentities().size());
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(index);
        // final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")); // METHOD 2
        logger.info("DVPAccountsOnSameNode select notary[{}]", notary);
        //create a transactionBuilder
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

        MealTicketType mealTicketType = new MealTicketType();
        Amount<FiatCurrency> mealTicketAmount = new Amount(quantityOfTicket, mealTicketType);

        //move bond to buyerAccountInfo account.
        PartyAndAmount mealTicketPartyAndAmount = new PartyAndAmount(buyerAccount, mealTicketAmount);

        //select the mealTicketToken which located in this shard
        QueryCriteria mealTicketCriteria = new QueryCriteria.VaultQueryCriteria().withStatus(Vault.StateStatus.UNCONSUMED)
                        .withExternalIds(Arrays.asList(sellerAccountInfo.getIdentifier().getId()))
                .withNotary(Arrays.asList(notary));

        //call utility function to move the bond from seller to buyer account
        //this also adds inputs and outputs to the transactionBuilder
        //till now we have only 1 transaction with 2 inputs and 2 outputs - one moving fungible tokens other moving non fungible tokens between accounts
        MoveTokensUtilitiesKt.addMoveFungibleTokens(transactionBuilder, getServiceHub(), Arrays.asList(mealTicketPartyAndAmount), sellerAccount, mealTicketCriteria);


        //Part2 : Move fungible token - cash from buyer to seller

        final EHKDType ehkdTokenType = new EHKDType();
//        QueryCriteria queryCriteriaForTokenBalance = QueryUtilitiesKt.heldTokenAmountCriteria(ehkdTokenType, buyerAccount).and(QueryUtilitiesKt.sumTokenCriteria());
//
//        List<Object> sum = getServiceHub().getVaultService().
//                queryBy(FungibleToken.class, queryCriteriaForTokenBalance).component5();
//
//        if(sum.size() == 0)
//            throw new FlowException(buyerAccountName + "has 0 token balance. Please ask the Bank to issue some cash.");
//        else {
//            Long tokenBalance = (Long) sum.get(0);
//            if(tokenBalance < costOfTicket)
//                throw new FlowException("Available token balance of " + buyerAccountName+ " is less than the cost of the ticket. Please ask the Bank to issue some cash if you wish to buy the ticket ");
//        }

        Amount<FiatCurrency> ehkdAmount = new Amount(costOfTicket, ehkdTokenType);

        //move money to sellerAccountInfo account.
        PartyAndAmount ehkdPartyAndAmount = new PartyAndAmount(sellerAccount, ehkdAmount);

        //construct the query criteria and get all available unconsumed fungible tokens which belong to buyers account
        QueryCriteria ehkdCriteria = new QueryCriteria.VaultQueryCriteria().withStatus(Vault.StateStatus.UNCONSUMED)
                        .withExternalIds(Arrays.asList(buyerAccountInfo.getIdentifier().getId()))
                .withNotary(Arrays.asList(notary));

        //call utility function to move the fungible token from buyer to seller account
        //this also adds inputs and outputs to the transactionBuilder
        //till now we have only 1 transaction with 2 inputs and 2 outputs - one moving fungible tokens other moving non fungible tokens between accounts
        MoveTokensUtilitiesKt.addMoveFungibleTokens(transactionBuilder, getServiceHub(), Arrays.asList(ehkdPartyAndAmount), buyerAccount, ehkdCriteria);

        //self sign the transaction. note : the host party will first self sign the transaction.
        SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder,
                Arrays.asList(getOurIdentity().getOwningKey()));

        //establish sessions with buyer and seller. to establish session get the host name from accountinfo object
        FlowSession buyerSession = initiateFlow(buyerAccountInfo.getHost());

        FlowSession sellerSession = initiateFlow(sellerAccountInfo.getHost());

        //Note: though buyer and seller are on the same node still we will have to call CollectSignaturesFlow as the signer is not a Party but an account.
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction,
                Arrays.asList(buyerSession, sellerSession)));

        //call ObserverAwareFinalityFlow for finality
        SignedTransaction stx = subFlow(new ObserverAwareFinalityFlow(fullySignedTx, Arrays.asList(buyerSession, sellerSession)));

        return ("The ticket is sold to "+buyerAccountName+", seller is "+ sellerAccountName + "\ntxID: " + stx.getId().toString());
    }

}

@InitiatedBy(DVPAccountsOnSameNode.class)
class DVPAccountsOnSameNodeResponder extends FlowLogic<Void> {

    private final FlowSession otherSide;

    public DVPAccountsOnSameNodeResponder(FlowSession otherSide) {
        this.otherSide = otherSide;
    }

    @Override
    @Suspendable
    public Void call() throws FlowException {

        subFlow(new SignTransactionFlow(otherSide) {
            @Override
            protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                // Custom Logic to validate transaction.
            }
        });

        subFlow(new ReceiveFinalityFlow(otherSide));

        return null;
    }
}
