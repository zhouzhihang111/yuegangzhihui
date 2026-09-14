package com.yuegang.zhihui.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.user.application.OrganizationService;
import com.yuegang.zhihui.user.application.UserIdGenerator;
import com.yuegang.zhihui.user.security.UserInternalServiceVerifier;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;

class InternalOrganizationTargetControllerTest {
    @Test
    void employeeTargetAcceptsEmployeeIdUserIdOrEmployeeNo() {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var organization = new OrganizationService(dataSource, new UserIdGenerator(7, Clock.systemUTC()));

            var department = organization.createDepartment(new CreateDepartmentRequest(null, "TRAIN", "培训部", 1));
            var position = organization.createPosition(new CreatePositionRequest("OPS", "运营", null));
            var employee = organization.createEmployee(new CreateEmployeeRequest(
                    "42", "E042", department.id(), Set.of(position.id()), LocalDate.of(2026, 8, 9)));
            var controller = new InternalOrganizationTargetController(dataSource, mock(UserInternalServiceVerifier.class));
            var request = new MockHttpServletRequest("GET", "/internal/v1/organization/targets");

            assertThat(controller.targets("EMPLOYEE", employee.id(), request).data()).containsExactly("42");
            assertThat(controller.targets("EMPLOYEE", "42", request).data()).containsExactly("42");
            assertThat(controller.targets("EMPLOYEE", "E042", request).data()).containsExactly("42");
            assertThat(controller.targets("POSITION", position.id(), request).data()).containsExactly("42");
        }
    }
}
