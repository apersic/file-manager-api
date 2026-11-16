# Specification

A browser file system in the shape of a folder tree. A file is a name plus `createdAt` and `updatedAt`. It has no bytes. There is no login.

This build creates folders and files, lists the children of a folder, reads a node with its ancestors, and searches by exact name or prefix. Delete is specified below and is not implemented yet.

## Planned behavior

A person can:

- create folders and folders inside them
- create a file in a folder
- list the children of a folder, one page at a time
- search by exact name in one folder or across the tree
- search by prefix while typing, on the server, one page at a time, with the first page sized at 10
- delete a file or a folder, and deleting a folder deletes everything inside it

Names keep the case they were typed with. Two siblings cannot use the same name when case is ignored. `/`, `\`, blank names, names that are only `.` or `..`, and leading or trailing spaces are rejected.

Search "in a folder" means the direct children of that folder, not every descendant. Prefix match is the start of the name, not a substring. `%` and `_` are normal characters.

The root folder is named Home. It is created by the migration. It cannot be deleted. The client asks `GET /api/v1/nodes/root` for its id.

Create returns 201. A duplicate sibling name returns 409, including a retry of a create that already succeeded. There is no idempotency header. Delete of a missing id returns 404. A second delete of the same id returns 404.

Listing and search use a cursor. The default page size is 50 and the cap is 100. Responses do not include a total count.

Create sends `parentId`, `kind` (`FILE` or `FOLDER`), and `name`. The 201 body is `id`, `parentId`, `kind`, `name`, `createdAt`, and `updatedAt`. `GET /api/v1/nodes/{id}` and `GET /api/v1/nodes/root` use that same body plus `ancestors`, each with `id`, `name`, and `kind`. A list is `GET /api/v1/nodes?parentId=&cursor=&limit=` and returns `items` plus `nextCursor`. Search is `GET /api/v1/nodes/search?q=&match=EXACT|PREFIX&parentId=&cursor=&limit=`. Omit `parentId` to search the whole tree. A missing node or parent is 404. A parent that is a file is 409. A cursor this API did not issue is 400. `limit` outside 1 to 100, a blank query, and a query longer than 255 characters are a 400 field map.

## Errors

Domain failures use this JSON body:

- `message`
- `status`
- `error`
- `timestamp`
- `path`

Bean validation uses a different 400 body: a map of field name to message. Unexpected failures return 500 with the message "An unexpected error occurred" and do not include the exception text. This split is deliberate. It matches the service this API is based on.

## Security

The API does not authenticate callers. The security filter still turns CSRF off, uses no session, allows only the configured browser origins, publishes `GET /actuator/health`, and denies every other actuator route.

## Data

One PostgreSQL table holds files and folders. A row points at its parent. The database rejects a file as a parent, rejects two siblings with the same folded name, and removes a subtree in one transaction when a folder is deleted. Flyway owns the schema. Hibernate only validates it.

`V1__create_node_catalog.sql` creates that table, seeds Home, and rejects a change to `parent_id`. Search reads that table. It does not add another one. Delete does not have an endpoint yet. The foreign key would already remove a subtree if a row were deleted in SQL.

## Tradeoffs already chosen

- Case folding uses PostgreSQL `lower` under the `C` collation, not a locale sort. `é` sorts after `z`.
- A huge folder delete is one transaction. It can hold locks for seconds. There is no background delete.
- Search can return folders as well as files. Each result will carry `kind`.
