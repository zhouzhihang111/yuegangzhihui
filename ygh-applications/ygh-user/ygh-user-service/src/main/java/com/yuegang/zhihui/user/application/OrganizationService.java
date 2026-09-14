package com.yuegang.zhihui.user.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.user.api.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class OrganizationService {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final UserIdGenerator ids;
    public OrganizationService(DataSource source,UserIdGenerator ids){this.jdbc=new JdbcTemplate(source);this.tx=new TransactionTemplate(new DataSourceTransactionManager(source));this.ids=ids;}
    public DepartmentView createDepartment(CreateDepartmentRequest request){try{long id=ids.nextId();Long parent=optional(request.parentId());jdbc.update("INSERT INTO user_department(id,parent_id,department_code,department_name,sort_order) VALUES(?,?,?,?,?)",id,parent,request.code(),request.name(),request.sortOrder());return department(id);}catch(DataIntegrityViolationException conflict){throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);}}
    public List<DepartmentView> departments(){return jdbc.query("SELECT * FROM user_department ORDER BY parent_id,sort_order,id",(rs,row)->mapDepartment(rs));}
    public PositionView createPosition(CreatePositionRequest request){try{long id=ids.nextId();jdbc.update("INSERT INTO user_position(id,position_code,position_name,description) VALUES(?,?,?,?)",id,request.code(),request.name(),request.description());return position(id);}catch(DataIntegrityViolationException conflict){throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);}}
    public List<PositionView> positions(){return jdbc.query("SELECT * FROM user_position ORDER BY position_name,id",(rs,row)->mapPosition(rs));}
    public List<EmployeeView> employees(){return jdbc.queryForList("SELECT id FROM user_employee ORDER BY employee_no,id",Long.class).stream().map(id->employee(id)).toList();}
    public EmployeeView createEmployee(CreateEmployeeRequest request){try{return tx.execute(status->{long id=ids.nextId(),user=positive(request.userId());jdbc.update("INSERT IGNORE INTO user_profile(user_id,display_name) VALUES(?,?)",user,"新用户");jdbc.update("INSERT INTO user_employee(id,user_id,employee_no,department_id,employment_status,hired_on) VALUES(?,?,?,?, 'ACTIVE',?)",id,user,request.employeeNo(),optional(request.departmentId()),request.hiredOn());replacePositions(id,request.positionIds());return employee(id);});}catch(DataIntegrityViolationException conflict){throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);}}
    public EmployeeView replacePositions(String employeeId,Set<String> positions){long id=positive(employeeId);return tx.execute(status->{replacePositions(id,positions);return employee(id);});}
    public EmployeeView changeStatus(String employeeId,String status){if(!Set.of("ACTIVE","SUSPENDED","LEFT").contains(status))throw new BusinessException(ErrorCode.VALIDATION_ERROR);long id=positive(employeeId);int changed=jdbc.update("UPDATE user_employee SET employment_status=?,left_on=CASE WHEN ?='LEFT' THEN CURRENT_DATE ELSE NULL END,version=version+1 WHERE id=?",status,status,id);if(changed!=1)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return employee(id);}
    private void replacePositions(long employeeId,Set<String> positions){jdbc.update("DELETE FROM user_employee_position WHERE employee_id=?",employeeId);if(positions==null)return;boolean primary=true;for(String value:new LinkedHashSet<>(positions)){jdbc.update("INSERT INTO user_employee_position(employee_id,position_id,primary_position) VALUES(?,?,?)",employeeId,positive(value),primary);primary=false;}}
    private DepartmentView department(long id){return jdbc.query("SELECT * FROM user_department WHERE id=?",rs->{if(!rs.next())throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return mapDepartment(rs);},id);}
    private PositionView position(long id){return jdbc.query("SELECT * FROM user_position WHERE id=?",rs->{if(!rs.next())throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return mapPosition(rs);},id);}
    private EmployeeView employee(long id){return jdbc.query("SELECT * FROM user_employee WHERE id=?",rs->{if(!rs.next())throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);Set<String> positions=new LinkedHashSet<>(jdbc.query("SELECT position_id FROM user_employee_position WHERE employee_id=? ORDER BY primary_position DESC,position_id",(x,row)->Long.toString(x.getLong(1)),id));Object d=rs.getObject("department_id");return new EmployeeView(Long.toString(id),Long.toString(rs.getLong("user_id")),rs.getString("employee_no"),d==null?null:d.toString(),positions,rs.getString("employment_status"),rs.getObject("hired_on",java.time.LocalDate.class),rs.getLong("version"));},id);}
    private static DepartmentView mapDepartment(ResultSet rs)throws SQLException{Object p=rs.getObject("parent_id");return new DepartmentView(Long.toString(rs.getLong("id")),p==null?null:p.toString(),rs.getString("department_code"),rs.getString("department_name"),rs.getInt("sort_order"),rs.getBoolean("enabled"),rs.getLong("version"));}
    private static PositionView mapPosition(ResultSet rs)throws SQLException{return new PositionView(Long.toString(rs.getLong("id")),rs.getString("position_code"),rs.getString("position_name"),rs.getString("description"),rs.getBoolean("enabled"),rs.getLong("version"));}
    private static Long optional(String value){return value==null||value.isBlank()?null:positive(value);}
    private static long positive(String value){try{long id=Long.parseLong(value);if(id<=0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new BusinessException(ErrorCode.VALIDATION_ERROR);}}
}
