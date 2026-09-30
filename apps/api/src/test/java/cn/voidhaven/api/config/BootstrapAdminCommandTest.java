package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


//该测试方法属于：单元测试
//单元测试的基础知识补充：
//单元测试是软件测试的一种，它是一种验证代码正确性的测试方法。
//单元测试的目的是验证代码的正确性，确保代码在各种情况下都能正常工作。
//单元测试的优点是：
//1. 可以快速发现代码中的问题
//2. 可以提高代码的质量
//3. 可以提高代码的可维护性
//4. 可以提高代码的可读性
//5. 可以提高代码的可测试性

// 单元测试的分类：
//1. 单元测试：测试单个类或方法的正确性
//2. 集成测试：测试多个类或方法的正确性
//3. 系统测试：测试整个系统的正确性
//4. 验收测试：测试整个系统的正确性
//5. 性能测试：测试整个系统的性能
//6. 安全测试：测试整个系统的安全性
//7. 兼容性测试：测试整个系统的兼容性
//8. 可用性测试：测试整个系统的可用性

// @Test：测试方法的注解，表示该方法是一个测试方法

// 单元测试的具体如何用？
    //1. 创建一个测试类
    //2. 创建一个测试方法
    //3. 使用@Test注解标记测试方法
    //4. 使用@ExtendWith注解标记测试类
    //5. 使用@ExtendWith注解标记测试方法
    //然后呢？测试方法的代码怎么写？
    //1. 使用@Test注解标记测试方法
    //2. 使用@ExtendWith注解标记测试类



@ExtendWith(OutputCaptureExtension.class)
//BootstrapAdminCommandTest：测试BootstrapAdminCommand类的功能
class BootstrapAdminCommandTest {


    @Test
    void requiresExplicitBootstrapAdminProfile() {
        Profile profile = BootstrapAdminCommand.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"bootstrap-admin"}, profile.value());
    }

//JUnit的测试结果输出格式，解释每个字段的含义：
//%TESTC  1 v2：表示测试用例的数量
//%TSTTREE2,cn.voidhaven.api.config.BootstrapAdminCommandTest,true,1,false,1,BootstrapAdminCommandTest,,[engine:junit-jupiter]/[class:cn.voidhaven.api.config.BootstrapAdminCommandTest]：表示测试用例的树形结构
//%TSTTREE3,acceptsProductionAndTestServerButRejectsLocal(cn.voidhaven.api.config.BootstrapAdminCommandTest),false,1,false,2,acceptsProductionAndTestServerButRejectsLocal(),,[engine:junit-jupiter]/[class:cn.voidhaven.api.config.BootstrapAdminCommandTest]/[method:acceptsProductionAndTestServerButRejectsLocal()]：表示测试用例的树形结构
//%TESTS  3,acceptsProductionAndTestServerButRejectsLocal(cn.voidhaven.api.config.BootstrapAdminCommandTest)：表示测试用例的数量
//%RUNTIME151：表示测试用例的运行时间

//根据该测试结果输出能得到什么？

//那测试用例运行是否成功从哪里看出来？
//从%TESTS  3,acceptsProductionAndTestServerButRejectsLocal(cn.voidhaven.api.config.BootstrapAdminCommandTest)：表示测试用例的数量
//如果%TESTS  3,acceptsProductionAndTestServerButRejectsLocal(cn.voidhaven.api.config.BootstrapAdminCommandTest)为0，则表示测试用例运行失败
//如果%TESTS  3,acceptsProductionAndTestServerButRejectsLocal(cn.voidhaven.api.config.BootstrapAdminCommandTest)为1，则表示测试用例运行成功

    @Test
    void acceptsProductionAndTestServerButRejectsLocal() {
        assertTrue(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"production", "bootstrap-admin"}));
        assertTrue(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"test-server", "bootstrap-admin"}));
        assertFalse(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"local", "bootstrap-admin"}));
        assertFalse(BootstrapAdminCommand.isApprovedEnvironment(new String[]{"test-server" + "-lookalike", "bootstrap-admin"}));
    }

    @Test
    void rejectsUnsupportedEnvironmentBeforeOpeningATransaction() {
        Fixture fixture = fixture("local");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.command.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("production or test-server"));
        verifyNoInteractions(fixture.transactions);
    }

    @Test
    void refusesToBootstrapWhenAnAccountAlreadyExists() {
        Fixture fixture = fixture("test-server", "bootstrap-admin");
        when(fixture.accounts.count()).thenReturn(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.command.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(error.getMessage().contains("accounts table is not empty"));
        verifyNoInteractions(fixture.activations);
    }

    @Test
    void testServerBootstrapPrintsTheOneTimeActivationUrlAndAuditsWithASyntheticRequestId(CapturedOutput output) {
        Fixture fixture = fixture("test-server", "bootstrap-admin");
        when(fixture.accounts.count()).thenReturn(0L);
        when(fixture.roles.findByCode("ADMIN")).thenReturn(Optional.empty());
        when(fixture.roles.findByCode("SUPER_ADMIN")).thenReturn(Optional.empty());
        when(fixture.roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.activations.issueLocked(any(Account.class))).thenReturn(
                new ActivationService.IssuedActivation("single-use-test-token", Instant.now().plusSeconds(3600)));

        fixture.command.run(new DefaultApplicationArguments(new String[0]));

        assertTrue(output.getOut().contains("http://203.0.113.10/auth/activate?token=single-use-test-token"));
        verify(fixture.audit).record(any(), any(), any(), any(), any(), any(), any(), isNull(),
                argThat(requestId -> requestId != null && requestId.length() == 26));
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(String... profiles) {
        AccountRepository accounts = mock(AccountRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ActivationService activations = mock(ActivationService.class);
        AuditService audit = mock(AuditService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        BootstrapAdminCommand command = new BootstrapAdminCommand(accounts, roles, activations, audit, environment, transactions);
        ReflectionTestUtils.setField(command, "adminLogin", "release-admin");
        ReflectionTestUtils.setField(command, "publicBaseUrl", "http://203.0.113.10");
        return new Fixture(command, accounts, roles, activations, audit, transactions);
    }

    private record Fixture(BootstrapAdminCommand command, AccountRepository accounts, RoleRepository roles,
                           ActivationService activations, AuditService audit, TransactionTemplate transactions) { }
}
