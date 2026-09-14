package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlywayMigrationPolicyTest {

    private final FlywayMigrationPolicy policy = new FlywayMigrationPolicy();

    @Test
    void acceptsPositiveNumericVersionAndLowerSnakeCaseDescription() {
        var first = policy.parse("db/migration/V1__create_initial_schema.sql");
        var twelfth = policy.parse("db\\migration\\V12__add_order_index.sql");

        assertThat(first.version()).isEqualTo(1L);
        assertThat(first.description()).isEqualTo("create_initial_schema");
        assertThat(first.resourcePath()).isEqualTo("db/migration/V1__create_initial_schema.sql");
        assertThat(twelfth.version()).isEqualTo(12L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "db/migration/V0__zero.sql",
        "db/migration/V01__leading_zero.sql",
        "db/migration/V1_bad_separator.sql",
        "db/migration/V1__.sql",
        "db/migration/V1__AddUser.sql",
        "db/migration/V1__add-user.sql",
        "db/migration/V1__添加用户.sql",
        "db/migration/V1__add user.sql",
        "db/migration/V1__valid.SQL",
        "migration/V1__wrong_directory.sql",
        "db/migration/../V1__path_traversal.sql"
    })
    void rejectsInvalidNames(String path) {
        assertThatThrownBy(() -> policy.parse(path))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.INVALID_NAME);
    }

    @Test
    void explicitlyRejectsUndoAndRepeatableMigrations() {
        assertCode("db/migration/U1__undo_order.sql", MigrationViolationCode.UNDO_SCRIPT_FORBIDDEN);
        assertCode("db/migration/R__refresh_view.sql", MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN);
    }

    @Test
    void rejectsMissingPath() {
        assertThatThrownBy(() -> policy.parse(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.parse(" "))
                .isInstanceOf(MigrationPolicyException.class);
    }

    @Test
    void reportsDuplicatePathAndVersionDeterministically() {
        var report = policy.validate(List.of(
                "db/migration/V2__second.sql",
                "db/migration/V1__first.sql",
                "db/migration/V1__duplicate_version.sql",
                "db/migration/V2__second.sql"));

        assertThat(report.valid()).isFalse();
        assertThat(report.violations())
                .extracting(MigrationViolation::code)
                .containsExactly(
                        MigrationViolationCode.DUPLICATE_VERSION,
                        MigrationViolationCode.DUPLICATE_RESOURCE);
    }

    @Test
    void comparesVersionsNumericallyAndRejectsHistoryInsertion() {
        assertThat(policy.validateNewMigrations(List.of(
                "db/migration/V10__tenth.sql"), 9L).valid()).isTrue();

        var report = policy.validateNewMigrations(List.of(
                "db/migration/V2__old.sql",
                "db/migration/V3__current.sql",
                "db/migration/V4__next.sql"), 3L);

        assertThat(report.violations())
                .extracting(MigrationViolation::code)
                .containsExactly(
                        MigrationViolationCode.OUT_OF_ORDER_VERSION,
                        MigrationViolationCode.OUT_OF_ORDER_VERSION);
    }

    @Test
    void emptyCatalogIsValidAndNullCatalogFailsFast() {
        assertThat(policy.validate(List.of()).valid()).isTrue();
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(NullPointerException.class);
    }

    private void assertCode(String path, MigrationViolationCode code) {
        assertThatThrownBy(() -> policy.parse(path))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(code);
    }
}
