package filemanager.repository;

import filemanager.domain.SearchMatch;

public final class NodeSearchSql {

  private NodeSearchSql() {}

  public static String select(
      boolean parentScoped, SearchMatch match, boolean upperBound, boolean resumed) {
    String predicate =
        switch (match) {
          case EXACT -> exactPredicate(resumed);
          case PREFIX -> prefixPredicate(upperBound, resumed);
        };
    String parent = parentScoped ? "parent_id = :parentId AND " : "";
    return """
        SELECT id, parent_id, kind, name, name_key, created_at, updated_at
        FROM node
        WHERE %s%s
        ORDER BY name_key, id
        LIMIT :limit"""
        .formatted(parent, predicate);
  }

  private static String exactPredicate(boolean resumed) {
    String match = "name_key = CAST(:lo AS varchar) COLLATE \"C\"";
    if (!resumed) {
      return match;
    }
    return match + " AND id > :lowerId";
  }

  private static String prefixPredicate(boolean upperBound, boolean resumed) {
    String range =
        resumed
            ? """
            (name_key > CAST(:lowerKey AS varchar) COLLATE "C"
              OR (name_key = CAST(:lowerKey AS varchar) COLLATE "C" AND id > :lowerId))"""
            : "name_key >= CAST(:lowerKey AS varchar) COLLATE \"C\"";
    if (!upperBound) {
      return range;
    }
    return range + " AND name_key < CAST(:hi AS varchar) COLLATE \"C\"";
  }
}
