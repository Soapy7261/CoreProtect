package net.coreprotect.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import net.coreprotect.api.result.SignResult;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;

/**
 * Provides API methods for sign text lookups.
 */
public class SignAPI {

    private SignAPI() {
        throw new IllegalStateException("API class");
    }

    public static List<SignResult> performLookup(Location location, int offset) {
        return performLookup(null, offset, 0, location);
    }

    public static List<SignResult> performLookup(Location location, int offset, int radius) {
        return performLookup(null, offset, radius, location);
    }

    public static List<SignResult> performLookup(String user, int offset) {
        return performLookup(user, offset, -1, null);
    }

    public static List<SignResult> performLookup(String user, int offset, int radius, Location location) {
        List<SignResult> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (location != null && location.getWorld() == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            Integer userId = MessageAPI.getUserId(connection, user);
            if (userId != null && userId == -1) {
                return result;
            }

            int checkTime = 0;
            if (offset > 0) {
                checkTime = (int) (System.currentTimeMillis() / 1000L) - offset;
            }

            StringBuilder query = new StringBuilder("SELECT time,user,wid,x,y,z,action,color,color_secondary,data,waxed,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8 FROM ");
            query.append(ConfigHandler.prefix).append("sign ");
            if (location != null) {
                query.append(WorldUtils.getWidIndex("sign"));
            }
            query.append("WHERE time > ? AND action = '1' AND (LENGTH(line_1) > 0 OR LENGTH(line_2) > 0 OR LENGTH(line_3) > 0 OR LENGTH(line_4) > 0 OR LENGTH(line_5) > 0 OR LENGTH(line_6) > 0 OR LENGTH(line_7) > 0 OR LENGTH(line_8) > 0)");

            if (userId != null) {
                query.append(" AND user = ?");
            }

            if (location != null) {
                query.append(" AND wid = ?");
                if (radius > 0) {
                    query.append(" AND x >= ? AND x <= ? AND z >= ? AND z <= ?");
                }
                else {
                    query.append(" AND x = ? AND y = ? AND z = ?");
                }
            }

            query.append(" ORDER BY rowid DESC");

            try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
                int parameterIndex = 1;
                statement.setInt(parameterIndex++, checkTime);

                if (userId != null) {
                    statement.setInt(parameterIndex++, userId);
                }

                if (location != null) {
                    int x = location.getBlockX();
                    int y = location.getBlockY();
                    int z = location.getBlockZ();
                    statement.setInt(parameterIndex++, WorldUtils.getWorldId(location.getWorld().getName()));

                    if (radius > 0) {
                        statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) x - radius));
                        statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) x + radius));
                        statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) z - radius));
                        statement.setInt(parameterIndex++, MessageAPI.clampToInt((long) z + radius));
                    }
                    else {
                        statement.setInt(parameterIndex++, x);
                        statement.setInt(parameterIndex++, y);
                        statement.setInt(parameterIndex, z);
                    }
                }

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        result.add(parseSignResult(connection, results));
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private static SignResult parseSignResult(Connection connection, ResultSet results) throws Exception {
        int userId = results.getInt("user");
        String username = ConfigHandler.playerIdCacheReversed.get(userId);
        if (username == null) {
            username = UserStatement.loadName(connection, userId);
        }

        String[] lines = new String[] {
                valueOrEmpty(results.getString("line_1")),
                valueOrEmpty(results.getString("line_2")),
                valueOrEmpty(results.getString("line_3")),
                valueOrEmpty(results.getString("line_4")),
                valueOrEmpty(results.getString("line_5")),
                valueOrEmpty(results.getString("line_6")),
                valueOrEmpty(results.getString("line_7")),
                valueOrEmpty(results.getString("line_8"))
        };

        return new SignResult(
                results.getLong("time"), username, WorldUtils.getWorldName(results.getInt("wid")),
                results.getInt("x"), results.getInt("y"), results.getInt("z"),
                results.getInt("action"), results.getInt("color"), results.getInt("color_secondary"),
                results.getInt("data"), results.getInt("waxed") == 1, results.getInt("face") == 0, lines
        );
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
