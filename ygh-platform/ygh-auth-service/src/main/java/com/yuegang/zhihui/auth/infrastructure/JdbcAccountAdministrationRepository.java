package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.*;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcAccountAdministrationRepository implements AccountAdministrationRepository {
    private final DataSource dataSource;
    public JdbcAccountAdministrationRepository(DataSource dataSource){this.dataSource=dataSource;}
    @Override public Optional<StatusChange> changeStatus(long userId,AccountStatus status,long expectedVersion,long operatorUserId,String reason){
        try(var connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                long accountId;
                try(var find=connection.prepareStatement("SELECT id FROM auth_account WHERE user_id=? FOR UPDATE")){
                    find.setLong(1,userId);try(var rows=find.executeQuery()){if(!rows.next()){connection.rollback();return Optional.empty();}accountId=rows.getLong(1);}
                }
                try(var update=connection.prepareStatement("UPDATE auth_account SET status=?,failed_login_count=0,locked_until=NULL,version=version+1 WHERE id=? AND version=?")){
                    update.setString(1,status.name());update.setLong(2,accountId);update.setLong(3,expectedVersion);
                    if(update.executeUpdate()!=1){connection.rollback();return Optional.empty();}
                }
                try(var audit=connection.prepareStatement("INSERT INTO auth_account_admin_audit(account_id,user_id,operator_user_id,action,reason) VALUES(?,?,?,?,?)")){
                    audit.setLong(1,accountId);audit.setLong(2,userId);audit.setLong(3,operatorUserId);audit.setString(4,status.name());
                    if(reason==null||reason.isBlank())audit.setNull(5,Types.VARCHAR);else audit.setString(5,reason.trim());audit.executeUpdate();
                }
                StatusChange result;
                try(var read=connection.prepareStatement("SELECT status,version,updated_at FROM auth_account WHERE id=?")){
                    read.setLong(1,accountId);try(var rows=read.executeQuery()){rows.next();result=new StatusChange(accountId,userId,AccountStatus.valueOf(rows.getString(1)),rows.getLong(2),rows.getTimestamp(3).toLocalDateTime().atOffset(ZoneOffset.UTC));}
                }
                connection.commit();return Optional.of(result);
            }catch(SQLException failure){connection.rollback();throw failure;}finally{connection.setAutoCommit(true);}
        }catch(SQLException failure){throw new AccountSecurityPersistenceException("failed to administer account",failure);}
    }
}
