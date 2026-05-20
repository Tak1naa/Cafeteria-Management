<font size=5>Project Backend Structure</font>

    2026.04.01 version 0.1

* <font size = 4>Controller</font>
    
Accept front-end requests, process parameters, and return responses. 


    Accept HTTP request
    Parameter authentication
    Call Service
    Return JSON/XML Response
    Exception handling

* <font size = 4>Service</font>

Handle core business logic.

    Implement business logic
    Transaction management
    Call Mapper/DAO
    Business exception handling

* <font size = 4>Mapper</font>

Interact with the database and perform SQL operations.

    Execute SQL statements
    Database CRUD operation
    Result set mapping
* <font size = 4>POJO</font>

The entity object corresponding to the database table.

    Mapping database table structure
    Carry the database qurey result
    Used for ORM framework(Mybatis,JPA)

* <font size = 4>DTO</font>

Transmitting data between different layers, especially receiving front-end parameters and returning responses.

    Receive the front-end request parameters
    Data verification
    Data returned to the front end
    Isolate entity classes to avoid exposing sensitive fields

* <font size = 4>VO</font>

Data objects dedicated to front-end presentation.

    Specifically tailored for the front end
    Aggregating multiple data sources
    Formatting and calculating fields

* <font size = 4>Utils</font>

Provides generic utility methods.

    Generic function encapsulation
    Static methods provide
    Reuse across projects

* <font size = 4>Config</font>

Spring Boot configuration classes.

    Third-party Integrated configuration
    Interceptor configuration
    Cross-domain configuration
    Security configuration

* <font size = 4>Exception</font>

Custom exception and global exception handling.

    Define business exceptions
    Global exception catching
    Uniform error return

