package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuegang.zhihui.common.core.PageRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MybatisPageAdapterTest {

    @Test
    void createsOneBasedCountingMybatisPageFromPublicContract() {
        var page = MybatisPageAdapter.toMybatisPage(new PageRequest(3, 100));

        assertThat(page.getCurrent()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.searchCount()).isTrue();
    }

    @Test
    void mapsEntitiesToDtosWithoutExposingPersistenceObjects() {
        var source = new Page<EntityRow>(2, 2, 5);
        source.setRecords(List.of(new EntityRow("A"), new EntityRow("B")));

        var response = MybatisPageAdapter.toPageResponse(source, row -> "dto-" + row.value());

        assertThat(response.records()).containsExactly("dto-A", "dto-B");
        assertThat(response.pageNo()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.pages()).isEqualTo(3);
    }

    @Test
    void emptyPageRetainsRequestedPageAndHasZeroPages() {
        var source = new Page<String>(7, 20, 0);
        source.setRecords(List.of());

        var response = MybatisPageAdapter.toPageResponse(source, value -> value);

        assertThat(response.records()).isEmpty();
        assertThat(response.pageNo()).isEqualTo(7);
        assertThat(response.pages()).isZero();
    }

    @Test
    void totalRemainsLongBeyondIntegerRange() {
        var source = new Page<String>(1, 100, (long) Integer.MAX_VALUE + 10L);
        source.setRecords(List.of("row"));

        var response = MybatisPageAdapter.toPageResponse(source, value -> value);

        assertThat(response.total()).isEqualTo((long) Integer.MAX_VALUE + 10L);
        assertThat(response.pages()).isEqualTo(21_474_837L);
    }

    @Test
    void responseRecordsAreDefensivelyCopied() {
        var records = new ArrayList<>(List.of("A"));
        var source = new Page<String>(1, 20, 1);
        source.setRecords(records);

        var response = MybatisPageAdapter.toPageResponse(source, value -> value);
        records.add("B");

        assertThat(response.records()).containsExactly("A");
        assertThatThrownBy(() -> response.records().add("C"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullInputsFailFastAtTheAdapterBoundary() {
        var source = new Page<String>(1, 20, 0);

        assertThatThrownBy(() -> MybatisPageAdapter.toMybatisPage(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MybatisPageAdapter.toPageResponse(null, value -> value))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MybatisPageAdapter.toPageResponse(source, null))
                .isInstanceOf(NullPointerException.class);
    }

    private record EntityRow(String value) {
    }
}
