/**
 * The User class represents a user with a username and password for authentication purposes.
 * This class provides methods to retrieve the username and password.
 * 
 * @author Ching, Nicolas Miguel T.
 * @author Santos, Kyle Adrian L.
 * @version 1.0
 * @since April 3, 2024
*/

public class User {
    private String username;
    private String password;

    /**
     * Constructs a new User object with the specified username and password.
     * 
     * @param username The username of the user
     * @param password The password of the user
    */

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Gets the username of the user.
     * 
     * @return The username
    */

    public String getUsername() {
        return username;
    }

    /**
     * Gets the password of the user.
     * 
     * @return The password
    */
    
    public String getPassword() {
        return password;
    }
}