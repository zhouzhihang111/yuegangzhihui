package com.yuegang.zhihui.user.application;

import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.AddressRepository;
import java.util.*;

public final class AddressService {
    private final AddressRepository repository; private final UserIdGenerator ids;
    public AddressService(AddressRepository repository,UserIdGenerator ids){this.repository=repository;this.ids=ids;}
    public List<AddressView> list(String userId){return repository.findAll(parse(userId));}
    public AddressView create(String userId,CreateAddressRequest request){return repository.create(ids.nextId(),parse(userId),request);}
    public AddressView update(String userId,String addressId,UpdateAddressRequest request){return repository.update(parse(addressId),parse(userId),request).orElseThrow(AddressService::conflict);}
    public AddressOperationResponse delete(String userId,String addressId,long version){if(!repository.delete(parse(addressId),parse(userId),version))throw conflict();return new AddressOperationResponse(true);}
    public AddressView makeDefault(String userId,String addressId,long version){return repository.makeDefault(parse(addressId),parse(userId),version).orElseThrow(AddressService::conflict);}
    private static BusinessException conflict(){return new BusinessException(ErrorCode.BUSINESS_CONFLICT);}
    private static long parse(String value){try{long id=Long.parseLong(value);if(id<=0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new BusinessException(ErrorCode.VALIDATION_ERROR);}}
}
