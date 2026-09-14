package com.yuegang.zhihui.user.domain;

import com.yuegang.zhihui.user.api.*;
import java.util.*;

public interface AddressRepository {
    List<AddressView> findAll(long userId);
    AddressView create(long id, long userId, CreateAddressRequest request);
    Optional<AddressView> update(long id, long userId, UpdateAddressRequest request);
    boolean delete(long id, long userId, long version);
    Optional<AddressView> makeDefault(long id, long userId, long version);
}
