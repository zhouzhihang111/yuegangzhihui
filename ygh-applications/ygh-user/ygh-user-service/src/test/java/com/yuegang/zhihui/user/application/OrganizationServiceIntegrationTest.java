package com.yuegang.zhihui.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.user.api.CreateDepartmentRequest;
import com.yuegang.zhihui.user.api.CreateEmployeeRequest;
import com.yuegang.zhihui.user.api.CreatePositionRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OrganizationServiceIntegrationTest {
    @Test
    void managesDepartmentPositionAndEmployeeLifecycle() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var service = new OrganizationService(dataSource, new UserIdGenerator(7, Clock.systemUTC()));

            var root = service.createDepartment(new CreateDepartmentRequest(null, "HQ", "总部", 1));
            var child = service.createDepartment(new CreateDepartmentRequest(root.id(), "TECH", "技术部", 2));
            assertThat(service.departments()).extracting(d -> d.code()).containsExactly("HQ", "TECH");
            assertThat(child.parentId()).isEqualTo(root.id());

            var developer = service.createPosition(new CreatePositionRequest("DEV", "开发工程师", "开发"));
            var reviewer = service.createPosition(new CreatePositionRequest("REVIEW", "评审员", null));
            assertThat(service.positions()).extracting(p -> p.code()).containsExactly("DEV", "REVIEW");

            var employee = service.createEmployee(new CreateEmployeeRequest(
                    "1", "E001", child.id(), Set.of(developer.id(), reviewer.id()), LocalDate.of(2026, 7, 1)));
            assertThat(dataSource.getConnection()).satisfies(connection -> {
                try (connection; var statement = connection.prepareStatement(
                        "SELECT display_name FROM user_profile WHERE user_id=1"); var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("新用户");
                }
            });
            assertThat(employee.positionIds()).containsExactlyInAnyOrder(developer.id(), reviewer.id());
            assertBusinessError(() -> service.createEmployee(new CreateEmployeeRequest(
                    "1", "E002", child.id(), Set.of(), LocalDate.of(2026, 7, 2))), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> service.createPosition(
                    new CreatePositionRequest("DEV", "重复岗位", null)), ErrorCode.BUSINESS_CONFLICT);
            assertThat(service.replacePositions(employee.id(), null).positionIds()).isEmpty();
            assertThat(service.replacePositions(employee.id(), Set.of(reviewer.id())).positionIds()).containsExactly(reviewer.id());
            assertThat(service.changeStatus(employee.id(), "SUSPENDED").status()).isEqualTo("SUSPENDED");
            assertThat(service.changeStatus(employee.id(), "LEFT").status()).isEqualTo("LEFT");

            assertBusinessError(() -> service.changeStatus(employee.id(), "UNKNOWN"), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> service.changeStatus("9223372036854775806", "ACTIVE"), ErrorCode.RESOURCE_NOT_FOUND);
            assertBusinessError(() -> service.replacePositions("0", Set.of()), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> service.replacePositions("not-a-number", Set.of()), ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
