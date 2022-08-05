package com.t20worldcup.webserver;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.t20worldcup.flows.CreateAndShareAccountFlow;
import com.t20worldcup.flows.IssueEHKDFlows;
import com.t20worldcup.flows.MoveTokensBetweenAccounts;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/token")
public class Controller {
    private final CordaRPCOps bankProxy;
    private final CordaRPCOps schoolProxy;
    private final CordaRPCOps studentProxy;

    private final Party bankParty;
    private final Party schoolParty;
    private final Party studentParty;
    private final static Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(NodeRPCConnection rpc) {
        this.bankProxy = rpc.bankProxy;
        this.schoolProxy = rpc.schoolProxy;
        this.studentProxy = rpc.studentProxy;

        bankParty = this.bankProxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Bank,L=Delhi,C=IN"));
        schoolParty = this.bankProxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=School,L=Delhi,C=IN"));
        studentParty = this.bankProxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Student,L=London,C=GB"));
    }



//    @GetMapping(value = "/all-accounts")
//    private List<String> getAllAccounts() {
//        List<AccountInfo>  result = null;
//        try {
//            result = this.proxy.startTrackedFlowDynamic(GetAllAccounts.class).getReturnValue().get();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//
//        List<String> accountInfoNames = new ArrayList<>();
//
//        for(AccountInfo accountInfo : result) {
//            accountInfoNames.add(accountInfo.getName());
//        }
//
//        return accountInfoNames;
//    }

//    @PostMapping(value = "/cash-balance")
//    private Long getCashBalanceForAccount(String accountId) {
//
//        UUID uuid = UUID.fromString(accountId);
//
//        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria().withStatus(Vault.StateStatus.UNCONSUMED).
//                withExternalIds(Arrays.asList(uuid));
//
//        List<StateAndRef<FungibleToken>>  list = this.proxy.vaultQueryByCriteria(criteria, FungibleToken.class).getStates();
//
//        Long totalBalance = 0L;
//
//        for(StateAndRef<FungibleToken> stateAndRef : list) {
//            totalBalance += stateAndRef.getState().getData().getAmount().getQuantity();
//        }
//
//        return totalBalance;
//    }
//
//    @PostMapping(value = "/is-account-owner-of-ticket")
//    private String isAccountOwnerOfTicket(String accountId, String nonFungibleTokenId) {
//
//        UUID uuid = UUID.fromString(accountId);
//
//        QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria().withStatus(Vault.StateStatus.UNCONSUMED).
//                withExternalIds(Arrays.asList(uuid));
//
//        List<StateAndRef<NonFungibleToken>> list = this.proxy.vaultQueryByCriteria(criteria, NonFungibleToken.class).getStates();
//
//        for(StateAndRef<NonFungibleToken> nonFungibleTokenStateAndRef : list) {
//            if (nonFungibleTokenStateAndRef.getState().getData().getLinearId().getId().equals(UUID.fromString(nonFungibleTokenId))) {
//                return "This account does hold the ticket";
//            }
//        }
//        return "This account does not hold the ticket";
//    }


    @GetMapping(value = "/createAccountForSchool")
    private String createAccountForSchool(String accountName) throws ExecutionException, InterruptedException {
        List<Party> partys = Arrays.asList(bankParty, studentParty);
        String s = this.schoolProxy.startFlowDynamic(CreateAndShareAccountFlow.class, accountName, partys).getReturnValue().get();
        return s;
    }


    @GetMapping(value = "/createAccountForStudent")
    private String createAccountForStudent(String accountName) throws ExecutionException, InterruptedException {
        List<Party> partys = Arrays.asList(bankParty, schoolParty);
        String s = this.studentProxy.startFlowDynamic(CreateAndShareAccountFlow.class, accountName, partys).getReturnValue().get();
        return s;
    }

    @GetMapping(value = "/issueEHKDForBank")
    private String createAccountForBank(String accountName, Long amount) throws ExecutionException, InterruptedException {
        SignedTransaction signedTransaction = this.bankProxy.startFlowDynamic(IssueEHKDFlows.Initiator.class, accountName, amount).getReturnValue().get();
        return signedTransaction.toString();
    }


    @GetMapping(value = "/moveTokensBetweenAccounts")
    private String moveTokensBetweenAccounts(String fromAccountName,String toAccountName, Long quantity) throws ExecutionException, InterruptedException {
        String s = this.studentProxy.startFlowDynamic(MoveTokensBetweenAccounts.class, fromAccountName, toAccountName, quantity).getReturnValue().get();
        return s;
    }
}
