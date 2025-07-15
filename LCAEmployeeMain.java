// Directory Structure:
// com.company.directory
// ├── models
// │   ├── Employee.java
// │   ├── GroupNode.java
// │   └── ErrorCode.java
// ├── service
// │   └── DirectoryService.java
// ├── store
// │   └── DirectoryStore.java
// ├── exceptions
// │   └── ServiceException.java
// └── Main.java

// models/Employee.java
package models;

import java.util.UUID;

public class Employee {
    private final UUID employeeId;
    private final String employeeName;

    public Employee(UUID employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee employee = (Employee) o;
        return employeeId.equals(employee.employeeId);
    }

    @Override
    public int hashCode() {
        return employeeId.hashCode();
    }
}

// models/GroupNode.java
package models;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class GroupNode {
    private final String groupName;
    private final Set<GroupNode> subGroups;
    private final Set<Employee> employeesInGroup;

    public GroupNode(String groupName) {
        this.groupName = groupName;
        this.subGroups = new CopyOnWriteArraySet<>();
        this.employeesInGroup = new CopyOnWriteArraySet<>();
    }

    public String getGroupName() {
        return groupName;
    }

    public Set<GroupNode> getSubGroups() {
        return subGroups;
    }

    public Set<Employee> getEmployeesInGroup() {
        return employeesInGroup;
    }

    public void addSubGroup(GroupNode group) {
        this.subGroups.add(group);
    }

    public void addEmployee(Employee employee) {
        this.employeesInGroup.add(employee);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupNode groupNode = (GroupNode) o;
        return groupName.equals(groupNode.groupName);
    }

    @Override
    public int hashCode() {
        return groupName.hashCode();
    }
}

// models/ErrorCode.java
package models;

public enum ErrorCode {
    INVALID_EMPLOYEE_LIST,
    EMPLOYEE_NOT_FOUND
}

// exceptions/ServiceException.java
package exceptions;

import models.ErrorCode;

public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;

    public ServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

// service/DirectoryService.java
package service;

import exceptions.ServiceException;
import models.Employee;
import models.ErrorCode;
import models.GroupNode;

import java.util.*;

public class DirectoryService {

    public GroupNode getCommonGroupForEmployees(GroupNode root, Set<Employee> targetEmployees) {
        if (root == null || targetEmployees == null || targetEmployees.isEmpty()) {
            throw new ServiceException(ErrorCode.INVALID_EMPLOYEE_LIST, "Input is invalid.");
        }

        Map<Employee, GroupNode> employeeToGroup = new HashMap<>();
        Map<GroupNode, GroupNode> parentMap = new HashMap<>();
        buildMaps(root, null, parentMap, employeeToGroup);

        List<GroupNode> paths = new ArrayList<>();
        for (Employee emp : targetEmployees) {
            if (!employeeToGroup.containsKey(emp)) {
                throw new ServiceException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee not found in directory: " + emp.getEmployeeName());
            }
            paths.add(employeeToGroup.get(emp));
        }

        return findLCA(paths, parentMap);
    }

    private void buildMaps(GroupNode node, GroupNode parent, Map<GroupNode, GroupNode> parentMap, Map<Employee, GroupNode> empMap) {
        if (parent != null) {
            parentMap.put(node, parent);
        }
        for (Employee e : node.getEmployeesInGroup()) {
            empMap.put(e, node);
        }
        for (GroupNode child : node.getSubGroups()) {
            buildMaps(child, node, parentMap, empMap);
        }
    }

    private GroupNode findLCA(List<GroupNode> nodes, Map<GroupNode, GroupNode> parentMap) {
        Set<GroupNode> path = new HashSet<>();
        GroupNode current = nodes.get(0);
        while (current != null) {
            path.add(current);
            current = parentMap.get(current);
        }

        for (int i = 1; i < nodes.size(); i++) {
            Set<GroupNode> tempPath = new HashSet<>();
            current = nodes.get(i);
            while (current != null) {
                tempPath.add(current);
                current = parentMap.get(current);
            }
            path.retainAll(tempPath);
        }

        GroupNode result = nodes.get(0);
        while (result != null && !path.contains(result)) {
            result = parentMap.get(result);
        }
        return result;
    }
}

// Main.java
import models.Employee;
import models.GroupNode;
import service.DirectoryService;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        // Setup group hierarchy
        GroupNode company = new GroupNode("Company");
        GroupNode hr = new GroupNode("HR");
        GroupNode engg = new GroupNode("Engg");
        GroupNode springs = new GroupNode("Springs");
        GroupNode fe = new GroupNode("FE");
        GroupNode be = new GroupNode("BE");

        company.addSubGroup(hr);
        company.addSubGroup(engg);
        engg.addSubGroup(springs);
        engg.addSubGroup(fe);
        springs.addSubGroup(be);

        // Employees
        Employee mona = new Employee(UUID.randomUUID(), "Mona");
        Employee alice = new Employee(UUID.randomUUID(), "Alice");
        Employee bob = new Employee(UUID.randomUUID(), "Bob");
        Employee lisa = new Employee(UUID.randomUUID(), "Lisa");
        Employee marley = new Employee(UUID.randomUUID(), "Marley");

        hr.addEmployee(mona);
        be.addEmployee(alice);
        be.addEmployee(bob);
        fe.addEmployee(lisa);
        fe.addEmployee(marley);

        DirectoryService service = new DirectoryService();

        GroupNode g1 = service.getCommonGroupForEmployees(company, Set.of(alice, bob));
        System.out.println("LCA(Alice, Bob) = " + g1.getGroupName());

        GroupNode g2 = service.getCommonGroupForEmployees(company, Set.of(alice, marley));
        System.out.println("LCA(Alice, Marley) = " + g2.getGroupName());

        GroupNode g3 = service.getCommonGroupForEmployees(company, Set.of(mona, lisa, bob));
        System.out.println("LCA(Mona, Lisa, Bob) = " + g3.getGroupName());
    }
}
