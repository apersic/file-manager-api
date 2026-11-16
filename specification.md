# Specification

A browser file system in the shape of a folder tree. A file is a name plus `createdAt` and `updatedAt`. It has no bytes. There is no login.

This service creates folders and files, lists the children of a folder, reads a node with its ancestors, searches by exact name or prefix, and deletes a file or a folder.

## Behavior

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

Create returns 201. A duplicate sibling name returns 409, including a retry of a create that already succeeded. There is no idempotency header. Delete of a missing id returns 404. A second delete of the same id returns 404. Delete of Home returns 409.

Listing and search use a cursor. The default page size is 50 and the cap is 100. Responses do not include a total count.

Create sends `parentId`, `kind` (`FILE` or `FOLDER`), and `name`. The 201 body is `id`, `parentId`, `kind`, `name`, `createdAt`, and `updatedAt`. `GET /api/v1/nodes/{id}` and `GET /api/v1/nodes/root` use that same body plus `ancestors`, each with `id`, `name`, and `kind`. A list is `GET /api/v1/nodes?parentId=&cursor=&limit=` and returns `items` plus `nextCursor`. Search is `GET /api/v1/nodes/search?q=&match=EXACT|PREFIX&parentId=&cursor=&limit=`. Omit `parentId` to search the whole tree. Delete is `DELETE /api/v1/nodes/{id}` and returns 204 with an empty body. A missing node or parent is 404. A parent that is a file is 409. A cursor this API did not issue is 400. `limit` outside 1 to 100, a blank query, and a query longer than 255 characters are a 400 field map.

## Decisions

- Files and folders share one `node` table. A row points at its parent.
- Home is the only root. A partial unique index keeps a second root from being inserted.
- A composite foreign key accepts a folder as a parent and rejects a file. `ON DELETE CASCADE` removes a subtree in the same transaction as the deleted row.
- `parent_id` cannot be updated. A trigger rejects that write, so a cycle cannot be added after insert.
- Names keep their case. Uniqueness, equality, sort, and prefix match use `name_key`, generated as `lower(name)` with the `C` collation.
- Sibling names are unique on `(parent_id, name_key)`. A duplicate insert uses `ON CONFLICT DO NOTHING` and the API returns 409.
- Prefix match is `name_key >= lo AND name_key < hi`, with `hi` the next code point. It is not `LIKE`.
- Listing and search are keyset pages ordered by `name_key`, then `id`. Search results include files and folders, and each row has `kind`.
- Flyway owns the schema. Hibernate validates it. `V1__create_node_catalog.sql` creates the table, both search indexes, and the parent-update trigger. `V2__reject_root_delete.sql` rejects a direct SQL delete of Home. The service returns 409 for that request before the database sees it.

## Errors

Domain failures use this JSON body:

- `message`
- `status`
- `error`
- `timestamp`
- `path`

Bean validation uses a different 400 body: a map of field name to message. Unexpected failures return 500 with the message "An unexpected error occurred" and do not include the exception text. This split is deliberate. It matches the service this API is based on. There is no 422.

## Security

The API does not authenticate callers. The security filter still turns CSRF off, uses no session, allows only the configured browser origins, publishes `GET /actuator/health`, and denies every other actuator route.

## Still unfinished

- A file has no bytes.
- There is no rename and no move.
- There is no login.
- A large folder delete stays one transaction and can hold locks for seconds. There is no background delete.
- This repository is the API. The browser client is a separate project.

## Tradeoffs already chosen

- Case folding uses PostgreSQL `lower` under the `C` collation, not a locale sort. `é` sorts after `z`.
- A huge folder delete is one transaction. It can hold locks for seconds. There is no background delete.
- Search returns folders as well as files. Each result carries `kind`.
