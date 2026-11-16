CREATE FUNCTION node_reject_root_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.parent_id IS NULL THEN
        RAISE EXCEPTION 'root folder cannot be deleted';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_node_root_delete
BEFORE DELETE ON node
FOR EACH ROW
EXECUTE FUNCTION node_reject_root_delete();
